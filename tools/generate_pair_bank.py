#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор банка пар «КиноНить» (D4, docs/debugging.md).

Банк = предвычисленные пары с известной глубиной для пула 117 актёров
(GamePairRepositoryImpl.PAIR_ACTORS). Д7: старт игры с глубиной 2 без
сети — 0 запросов к TMDB.

Один файл, только stdlib Python 3 (pip-пакетов нет).
Запускать на машине с доступом к api.themoviedb.org (VPN):

    python tools/generate_pair_bank.py --selftest     # офлайн: самопроверка логики (без VPN и ключа)
    python tools/generate_pair_bank.py --limit 10     # смоук: первые 10 актёров
    python tools/generate_pair_bank.py                # полный: пары d=2 (по умолчанию)
    python tools/generate_pair_bank.py --depth3 --yes # + d=3 (долго; оценка печатиется перед стартом)

Ключ TMDB: env TMDB_API_KEY, либо --key KEY, либо константа TMDB_API_KEY
ниже, иначе — интерактивный ввод. Ключ ни в выходной файл, ни в кэш не
попадает (ключ кэша — sha1 пути без ключа).

Метод, фаза 1 (d=2):
  1. Фильмографии актёров: /person/{id}/movie_credits — только cast,
     без пагинации (точно как приложение: ActorRepositoryImpl).
  2. Составы фильмов: /movie/{id}/credits — только cast (crew игнорируем,
     MovieRepositoryImpl).
  3. Множества ко-актёров. Для каждой упорядоченной пары (A, B) без
     общего фильма (d=1 — тривиальная, приложение её отбраковывает) с
     общим ко-актёром X — путь [fA, X, fB]. По одному пути на пару
     (минимальные ID) — детерминированность. fA ≠ fB автоматически:
     равенство означало бы общий фильм (d=1).

Фаза 2 (--depth3): для пар без d=2 — путь [f1, X, f2, Y, f3]:
X — ко-актёр A с пулевой сосуществованностью >= --d3-min-overlap (по
умолчанию 2), Y — ко-актёр B, f2 — фильм с X и Y. Медленнее: для X —
его фильмография + составы его фильмов (оценка ~100–500k запросов,
несколько часов при 50/10s). Перед стартом — оценка и подтверждение
(--yes). Банк сохраняется после фазы 1; фаза 2 — инкрементально
(Ctrl-C безопасен).

Выход (контракт D7): debugging/pairs_bank_v1.json
    {"version": 1, "pool": [117 ID], "names": {"ID": имя},
     "pairs": [{"s": A, "t": B, "d": 2, "p": [fA, X, fB]},
               {"s": A, "t": B, "d": 3, "p": [f1, X, f2, Y, f3]}]}
Только ID и имена, без ключей. names — строковые ключи (ограничение
JSON-объекта).

Кэш: tools/.pair_bank_cache/ (gitignored) — сырые JSON-ответы;
повторные прогоны почти бесплатны. Rate-limit: 50 запросов / 10 c.
"""

import getpass
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import deque

# ============================ настройки ============================

# Ключ можно вписать сюда, либо через env TMDB_API_KEY / --key,
# иначе скрипт спросит при запуске.
TMDB_API_KEY = ""

API = "https://api.themoviedb.org/3"
LANGUAGE = "en-US"

CACHE_DIR = "tools/.pair_bank_cache"
OUT_DEFAULT = "debugging/pairs_bank_v1.json"

RATE_LIMIT = 50        # максимум запросов за RATE_WINDOW_S
RATE_WINDOW_S = 10.0

D3_MIN_OVERLAP = 2     # фаза 2: мин. число фильмов пула, в которых есть X

# Пул 117 (D2) — ТОЧНО GamePairRepositoryImpl.PAIR_ACTORS:
# 107 из tools/generate_start_pool.py (start_pool_130, 97 популярных
# фильмов, пересечение кастов >= 2) + 10 legacy-звёзд.
# При регенерации пула — обновить список и перезапустить скрипт.
POOL = [
    16851, 3223, 16828, 505710, 1136406, 103, 1813, 2283, 9281, 27972,
    55636, 57755, 72129, 74568, 237455, 530025, 1190668, 1211946, 1373737, 1233,
    1245, 1640, 2954, 3810, 3895, 6162, 8691, 9048, 15277, 20089,
    22970, 44079, 55936, 71580, 83002, 89599, 91606, 96066, 117642, 225730,
    543530, 932967, 933238, 1880755, 19, 192, 515, 887, 1229, 1231,
    1892, 1896, 2037, 2231, 2963, 3141, 3894, 8210, 10401, 10980,
    10989, 11288, 12052, 13014, 17052, 17604, 20766, 22226, 27578, 30082,
    38673, 43667, 45405, 55585, 56446, 58502, 71070, 77069, 79006, 83854,
    100766, 110298, 111640, 111691, 121716, 167564, 172069, 205923, 220914, 457386,
    513677, 543261, 561869, 986808, 1091324, 1161474, 1477958, 1516266, 1542283, 1649152,
    1731537, 2028037, 2430993, 2569691, 2593824, 4019851, 4389435,
    # legacy-звёзды (MVP), не входят в сгенерированный пул:
    31, 38, 39, 42, 122, 287, 385, 5381, 9226, 2518,
]


# ============================ лимит и счётчики ============================

class RateLimiter:
    """Скользящее окно: не более RATE_LIMIT запросов за RATE_WINDOW_S."""

    def __init__(self):
        self._times = deque()

    def wait(self) -> None:
        now = time.monotonic()
        if len(self._times) >= RATE_LIMIT:
            sleep_for = RATE_WINDOW_S - (now - self._times[0])
            if sleep_for > 0:
                time.sleep(sleep_for)
                now = time.monotonic()
        self._times.append(now)


class Stats:
    def __init__(self):
        self.network = 0
        self.cache_hits = 0
        self.start = time.monotonic()

    def elapsed_min(self) -> float:
        return (time.monotonic() - self.start) / 60.0


# ============================ TMDB ============================

def cache_file_for(path: str, params: dict) -> str:
    """Путь кэша: sha1 от 'path?params' БЕЗ ключа."""
    q = urllib.parse.urlencode(params)
    digest = hashlib.sha1(f"{path}?{q}".encode("utf-8")).hexdigest()
    return os.path.join(CACHE_DIR, digest + ".json")


def get_json(key, path, params, limiter, stats):
    """GET с кэшем, rate-limit и ретраями. 404 → None (кашируется)."""
    fp = cache_file_for(path, params)
    if os.path.exists(fp):
        with open(fp, encoding="utf-8") as f:
            raw = f.read()
        stats.cache_hits += 1
        return None if raw == "" else json.loads(raw)

    full_params = dict(params)
    full_params["api_key"] = key
    url = f"{API}/{path}?{urllib.parse.urlencode(full_params)}"
    for attempt in range(4):
        limiter.wait()
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                data = json.loads(r.read().decode("utf-8"))
            stats.network += 1
            os.makedirs(CACHE_DIR, exist_ok=True)
            with open(fp, "w", encoding="utf-8") as f:
                json.dump(data, f)
            return data
        except urllib.error.HTTPError as e:
            stats.network += 1
            if e.code == 401:
                sys.exit("TMDB-ключ отклонён (401). Проверь TMDB_API_KEY.")
            if e.code == 404:
                os.makedirs(CACHE_DIR, exist_ok=True)
                with open(fp, "w", encoding="utf-8"):
                    pass  # пустой файл = маркер «не существует»
                return None
            if e.code == 429:
                try:
                    wait = int(float(e.headers.get("Retry-After", "10")))
                except ValueError:
                    wait = 10
                print(f"  429 rate-limit на {path} — ждём {wait} c")
                time.sleep(wait)
                continue
            if 500 <= e.code < 600:
                time.sleep(2 * (attempt + 1))
                continue
            raise
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError):
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Не удалось получить {path} после 4 попыток (VPN?)")


def fetch_person_credits(key, pid, limiter, stats):
    """Фильмография актёра (cast, без пагинации) — как в приложении.
    → {"name": str|None, "films": [movie_id...]} или None при 404."""
    data = get_json(key, f"person/{pid}/movie_credits",
                    {"language": LANGUAGE}, limiter, stats)
    if data is None:
        return None
    films = sorted({c["id"] for c in (data.get("cast") or [])
                    if c.get("id") is not None})
    return {"name": data.get("name"), "films": films}


def fetch_movie_cast(key, mid, limiter, stats):
    """Состав фильма (cast; crew игнорируем) — как в приложении.
    → [person_id...] или [] при 404."""
    data = get_json(key, f"movie/{mid}/credits",
                    {"language": LANGUAGE}, limiter, stats)
    if data is None:
        return []
    return [c["id"] for c in (data.get("cast") or []) if c.get("id") is not None]


# ============================ логика (чистые функции) ============================

def build_coactors(pool, filmographies, casts):
    """coactors[a] = {x: [фильмы a с x, по возрастанию]}.
    Собирается из cast фильмов a (сам актёр тоже входит в cast своих
    фильмов — концы пути отбрасываем при выборе)."""
    coactors = {}
    for a in pool:
        by_x = {}
        for f in filmographies.get(a, []):
            for x in casts.get(f, []):
                by_x.setdefault(x, set()).add(f)
        coactors[a] = {x: sorted(fs) for x, fs in by_x.items()}
    return coactors


def find_d2_pairs(pool, filmographies, coactors):
    """Упорядоченные пары (a, b), a ≠ b, с путём d=2 [fA, X, fB].
    Пары с общим фильмом (d=1) НЕ возвращаются — учтены в d1_count.
    Детерминированно: X = мин. ID, fA/fB = мин. ID фильмов.
    → (pairs: [dict], d1_count: int)"""
    pairs = []
    d1_count = 0
    films_of = {a: set(filmographies.get(a, [])) for a in pool}
    cokeys = {a: set(coactors[a]) for a in pool}
    for a in pool:
        fa_set = films_of[a]
        ca = coactors[a]
        ka = cokeys[a]
        for b in pool:
            if a == b:
                continue
            if fa_set & films_of[b]:
                d1_count += 1
                continue
            common = ka & cokeys[b]
            common.discard(a)
            common.discard(b)
            if not common:
                continue
            x = min(common)
            pairs.append({"s": a, "t": b, "d": 2,
                          "p": [ca[x][0], x, coactors[b][x][0]]})
    return pairs, d1_count


def find_d3_path(a, b, coactors, get_films, get_cast, cooccurrence, min_overlap):
    """Путь d=3 для пары без d=2: [f1, X, f2, Y, f3].
    X — ко-актёр a с сосуществованностью >= min_overlap (сначала больше,
    затем по ID); Y — ко-актёр b в фильме f2, где есть X.
    get_films(x)/get_cast(f) — ленивые доступы (с кэшем).
    → [f1, X, f2, Y, f3] или None. Детерминированно."""
    ca, cb = coactors[a], coactors[b]
    cands = [x for x in ca
             if x not in (a, b) and cooccurrence.get(x, 0) >= min_overlap]
    cands.sort(key=lambda x: (-cooccurrence.get(x, 0), x))
    for x in cands:
        for f2 in get_films(x):
            for y in sorted(get_cast(f2)):
                if y in (a, b) or y not in cb:
                    continue
                return [ca[x][0], x, f2, y, cb[y][0]]
    return None


def d3_pending(pool, filmographies, pairs_d2):
    """Упорядоченные пары без общего фильма и без d=2 — кандидаты в d=3."""
    have2 = {(p["s"], p["t"]) for p in pairs_d2}
    films_of = {a: set(filmographies.get(a, [])) for a in pool}
    out = []
    for a in pool:
        for b in pool:
            if a == b or (a, b) in have2 or films_of[a] & films_of[b]:
                continue
            out.append((a, b))
    return out


def build_bank(pool, names, pairs):
    """JSON банка (контракт D7): только ID и имена, без ключей."""
    return {
        "version": 1,
        "pool": list(pool),
        "names": {str(a): (names.get(a) or f"Actor {a}") for a in pool},
        "pairs": pairs,
    }


def save_bank(path, bank) -> None:
    d = os.path.dirname(path)
    if d:
        os.makedirs(d, exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(bank, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


# ============================ самопроверка (офлайн) ============================

def selftest() -> None:
    """Офлайн-проверка логики без сети и ключа."""
    pool = [1, 2, 3, 4]
    casts = {
        100: [1, 9],
        101: [9, 2],
        102: [1, 2],    # общий 1 и 2 → d=1
        103: [3, 9],
        104: [2, 7],
        105: [7, 3],
        200: [1, 5],
        201: [5, 4],
        202: [4, 6],
        300: [6, 3],
    }
    filmographies = {
        1: [100, 102, 200],
        2: [101, 102, 104],
        3: [103, 105, 300],
        4: [201, 202],
    }
    films_by_person = {}
    for f, cs in casts.items():
        for x in cs:
            films_by_person.setdefault(x, set()).add(f)
    films_by_person = {x: sorted(fs) for x, fs in films_by_person.items()}
    cooccurrence = {x: len(fs) for x, fs in films_by_person.items()}

    checks = 0

    def ok(cond, msg):
        nonlocal checks
        if not cond:
            raise AssertionError(msg)
        checks += 1

    # Пул: ровно 117, без дублей (D2)
    ok(len(POOL) == 117, f"пул: ждём 117 актёров, получено {len(POOL)}")
    ok(len(set(POOL)) == 117, "пул: дубли ID")

    coactors = build_coactors(pool, filmographies, casts)
    pairs, d1_count = find_d2_pairs(pool, filmographies, coactors)

    expected_d2 = {
        (1, 3): [100, 9, 103],
        (3, 1): [103, 9, 100],
        (1, 4): [200, 5, 201],
        (4, 1): [201, 5, 200],
        (2, 3): [104, 7, 105],
        (3, 2): [105, 7, 104],
        (3, 4): [300, 6, 202],
        (4, 3): [202, 6, 300],
    }
    got_d2 = {(p["s"], p["t"]): p["p"] for p in pairs}
    ok(got_d2 == expected_d2, f"d2: ждали {expected_d2}, получено {got_d2}")
    ok(d1_count == 2, f"d1: ждём 2, получено {d1_count}")
    ok((1, 2) not in got_d2 and (2, 1) not in got_d2,
       "d1-пара (общий фильм) попала в банк")

    # Любой d2-путь проходимо: a∈cast(fA), x∈cast(fA), x∈cast(fB), b∈cast(fB)
    for (a, b), (fa, x, fb) in got_d2.items():
        ok(a in casts[fa] and x in casts[fa]
           and x in casts[fb] and b in casts[fb],
           f"d2-путь ({a},{b}) непроходимо: {(fa, x, fb)}")
        ok(fa != fb, f"d2-путь ({a},{b}): fA == fB")
        ok(x not in (a, b), f"d2-путь ({a},{b}): X — конец пары")

    # d3
    pending = d3_pending(pool, filmographies, pairs)
    ok(sorted(pending) == [(2, 4), (4, 2)], f"pending d3: {pending}")

    def get_films(x):
        return films_by_person.get(x, [])

    def get_cast(f):
        return casts.get(f, [])

    ok(find_d3_path(2, 4, coactors, get_films, get_cast, cooccurrence, 1)
       == [102, 1, 200, 5, 201], "d3 (2,4)")
    ok(find_d3_path(4, 2, coactors, get_films, get_cast, cooccurrence, 1)
       == [201, 5, 200, 1, 102], "d3 (4,2)")

    for (a, b), path in {
        (2, 4): [102, 1, 200, 5, 201],
        (4, 2): [201, 5, 200, 1, 102],
    }.items():
        f1, x, f2, y, f3 = path
        ok(len({f1, f2, f3}) == 3, f"d3 ({a},{b}): фильмы не различны")
        ok(a in casts[f1] and x in casts[f1] and x in casts[f2]
           and y in casts[f2] and y in casts[f3] and b in casts[f3],
           f"d3 ({a},{b}): непроходимо")
        ok(x not in (a, b) and y not in (a, b),
           f"d3 ({a},{b}): середина совпадает с концом")

    # Детерминированность
    ok(find_d3_path(2, 4, coactors, get_films, get_cast, cooccurrence, 1)
       == find_d3_path(2, 4, coactors, get_films, get_cast, cooccurrence, 1),
       "d3: недетерминированно")
    ok(find_d2_pairs(pool, filmographies, coactors)[0] == pairs,
       "d2: недетерминированно")

    # Филтр сосуществованности: у (4,2) нет X с cooccurrence >= 3
    ok(find_d3_path(4, 2, coactors, get_films, get_cast, cooccurrence, 3) is None,
       "d3: фильтр min_overlap не сработал")
    ok(find_d3_path(2, 4, coactors, get_films, get_cast, cooccurrence, 3)
       == [102, 1, 200, 5, 201], "d3 (2,4) при min_overlap=3")

    # Схема JSON банка (контракт D7)
    names = {1: "One", 2: "Two", 3: "Three", 4: "Four"}
    bank = build_bank(pool, names,
                      pairs + [{"s": 2, "t": 4, "d": 3, "p": [102, 1, 200, 5, 201]}])
    ok(set(bank) == {"version", "pool", "names", "pairs"},
       f"ключи банка: {set(bank)}")
    ok(bank["version"] == 1 and bank["pool"] == pool, "банк: version/pool")
    ok(set(bank["names"]) == {str(a) for a in pool}, "банк: ключи names")
    for p in bank["pairs"]:
        ok(set(p) == {"s", "t", "d", "p"}, f"ключи пары: {set(p)}")
        ok(len(p["p"]) == (3 if p["d"] == 2 else 5), f"длина p: {p}")
        ok(all(isinstance(v, int) for v in p["p"]), "p: не все int")
    dump = json.dumps(bank, ensure_ascii=False)
    ok("api_key" not in dump.lower(), "банк: просочился ключ")

    print(f"SELFTEST OK: {checks} проверок (d1/d2/d3, схема, детерминизм, пул=117)")


# ============================ фазы ============================

def load_key(cli_key):
    if cli_key:
        return cli_key.strip()
    env = os.environ.get("TMDB_API_KEY", "").strip()
    if env:
        return env
    if TMDB_API_KEY:
        return TMDB_API_KEY.strip()
    try:
        return getpass.getpass("TMDB API key: ").strip()
    except (EOFError, KeyboardInterrupt):
        sys.exit("\nКлюч не задан — выход.")


def run_phase1(key, pool, limiter, stats):
    """Фильмографии + составы + пары d=2.
    → (filmographies, casts, coactors, pairs_d2, d1_count, names, cooccurrence)"""
    print(f"1/3 Фильмографии {len(pool)} актёров...")
    filmographies, names = {}, {}
    for i, a in enumerate(pool, 1):
        pc = fetch_person_credits(key, a, limiter, stats)
        if pc is None:
            print(f"  WARNING: актёр {a} — 404, фильмографии нет "
                  f"(пар с ним не будет)")
            filmographies[a] = []
            names[a] = f"Actor {a}"
        else:
            filmographies[a] = pc["films"]
            names[a] = pc["name"] or f"Actor {a}"
        if i % 10 == 0 or i == len(pool):
            print(f"  {i}/{len(pool)}")

    all_films = sorted({f for fs in filmographies.values() for f in fs})
    est_min = len(all_films) * RATE_WINDOW_S / RATE_LIMIT / 60
    print(f"2/3 Составы {len(all_films)} фильмов (~{len(all_films)} запросов, "
          f"~{est_min:.0f} мин при 50/10s)...")
    casts = {}
    for i, f in enumerate(all_films, 1):
        casts[f] = fetch_movie_cast(key, f, limiter, stats)
        if i % 200 == 0 or i == len(all_films):
            print(f"  {i}/{len(all_films)}")

    coactors = build_coactors(pool, filmographies, casts)
    cooc = {}
    for a in pool:
        for f in filmographies[a]:
            for x in casts.get(f, []):
                cooc.setdefault(x, set()).add(f)
    cooccurrence = {x: len(fs) for x, fs in cooc.items()}

    print("3/3 Подсчёт пар d=2...")
    pairs, d1_count = find_d2_pairs(pool, filmographies, coactors)
    return filmographies, casts, coactors, pairs, d1_count, names, cooccurrence


def run_phase2(key, pool, filmographies, coactors, cooccurrence, pairs_d2,
               limiter, stats, min_overlap, out_path, names, yes):
    """d=3 для пар без d=2: оценка + подтверждение + инкрементальные сохранения."""
    pending = d3_pending(pool, filmographies, pairs_d2)
    if not pending:
        print("Фаза 2: пар без d=2 нет — делать нечего.")
        return []
    cands = {x for a in pool for x in coactors[a]
             if cooccurrence.get(x, 0) >= min_overlap}
    avg_films = max(1, sum(len(f) for f in filmographies.values()) // len(pool))
    est = len(cands) * (1 + avg_films)
    est_min = est * RATE_WINDOW_S / RATE_LIMIT / 60
    print(f"\nФаза 2 (d=3): {len(pending)} упорядоченных пар без d=2, "
          f"{len(cands)} кандидатных мостов X (сосуществование >= {min_overlap}).")
    print(f"Оценка-верх: ~{est} запросов (~{est_min:.0f} мин при 50/10s). "
          f"Кэш и ранние остановки снижают реальную стоимость; "
          f"Ctrl-C безопасен (банк после фазы 1 уже сохранён).")
    if not yes:
        try:
            ans = input("Продолжить? [y/N] ").strip().lower()
        except EOFError:
            ans = ""
        if ans not in ("y", "yes"):
            sys.exit("Фаза 2 отменена. Для запуска без подтверждения: --yes.")

    d3_pairs = []
    t0 = time.monotonic()

    def get_films(x):
        pc = fetch_person_credits(key, x, limiter, stats)
        return pc["films"] if pc else []

    for i, (a, b) in enumerate(pending, 1):
        path = find_d3_path(a, b, coactors, get_films,
                            lambda f: fetch_movie_cast(key, f, limiter, stats),
                            cooccurrence, min_overlap)
        if path is not None:
            d3_pairs.append({"s": a, "t": b, "d": 3, "p": path})
        if i % 200 == 0 or i == len(pending):
            el = (time.monotonic() - t0) / 60.0
            print(f"  {i}/{len(pending)} (найдено d3: {len(d3_pairs)}, "
                  f"{el:.1f} мин)")
            save_bank(out_path, build_bank(pool, names, pairs_d2 + d3_pairs))
    return d3_pairs


# ============================ main ============================

def parse_args(argv):
    opts = {
        "key": None, "limit": 0, "depth3": False, "yes": False,
        "selftest": False, "out": OUT_DEFAULT,
        "d3_min_overlap": D3_MIN_OVERLAP,
    }
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--selftest":
            opts["selftest"] = True
        elif a == "--depth3":
            opts["depth3"] = True
        elif a == "--yes":
            opts["yes"] = True
        elif a in ("-h", "--help"):
            print(__doc__)
            sys.exit(0)
        elif a == "--limit":
            i += 1
            opts["limit"] = int(argv[i])
        elif a == "--key":
            i += 1
            opts["key"] = argv[i]
        elif a == "--out":
            i += 1
            opts["out"] = argv[i]
        elif a == "--d3-min-overlap":
            i += 1
            opts["d3_min_overlap"] = int(argv[i])
        else:
            sys.exit(f"Неизвестный аргумент: {a} (см. --help)")
        i += 1
    return opts


def main() -> None:
    opts = parse_args(sys.argv[1:])
    if opts["selftest"]:
        selftest()
        return
    key = load_key(opts["key"])
    pool = POOL[:opts["limit"]] if opts["limit"] > 0 else POOL
    if len(pool) < 2:
        sys.exit("Нужно минимум 2 актёра: --limit >= 2.")
    if opts["limit"] > 0:
        print(f"СМОК-РЕЖИМ: только первые {len(pool)} актёров (банк НЕ полный)!")

    limiter, stats = RateLimiter(), Stats()
    (filmographies, casts, coactors, pairs_d2, d1_count,
     names, cooccurrence) = run_phase1(key, pool, limiter, stats)
    save_bank(opts["out"], build_bank(pool, names, pairs_d2))

    total = len(pool) * (len(pool) - 1)
    print("\n=== БАНК ПАР: СТАТИСТИКА ===")
    print(f"Актёров в пуле: {len(pool)}")
    print(f"Запросы: сеть={stats.network}, кэш={stats.cache_hits}, "
          f"время={stats.elapsed_min():.1f} мин")
    print(f"Пар d=1 (общий фильм, в банк не попадают): {d1_count}")
    print(f"Пар d=2 в банке: {len(pairs_d2)}/{total} "
          f"({100.0 * len(pairs_d2) / total:.1f}%)")
    print(f"Пар без d=2 (онлайн-BFS, либо --depth3): "
          f"{total - d1_count - len(pairs_d2)}")
    print(f"Выход: {opts['out']}")

    if opts["depth3"]:
        d3_pairs = run_phase2(key, pool, filmographies, coactors, cooccurrence,
                              pairs_d2, limiter, stats, opts["d3_min_overlap"],
                              opts["out"], names, opts["yes"])
        print(f"\nПар d=3 в банке: {len(d3_pairs)} (всего "
              f"{len(pairs_d2) + len(d3_pairs)}; запросов всего="
              f"{stats.network}, время={stats.elapsed_min():.1f} мин)")


if __name__ == "__main__":
    main()