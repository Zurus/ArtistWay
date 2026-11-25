#!/usr/bin/env python3
"""
Самодостаточный генератор списка актёров (TMDB person_id) для «КиноСвязи».

Без привязки к проекту: один файл, только стандартная библиотека Python 3.
Просто скопируйте на машину с VPN и запустите:

    python generate_start_pool.py

Ключ TMDB — один из трёх способов:
  1) вписать в константу TMDB_API_KEY ниже;
  2) оставить пустым — скрипт запросит его при запуске (ввод скрытый);
  3) передать аргументом: python generate_start_pool.py ВАШ_КЛЮЧ

Метод отбора:
  1. Берём N популярных фильмов TMDB (`/movie/popular`, страницы 1..PAGES).
  2. Для каждого фильма — состав (топ CAST_LIMIT по порядку).
  3. Считаем пересечения: актёр, играющий в >= MIN_MOVIES фильмах выборки,
     гарантированно пересекается с другими актёрами списка
     (достижимость пары за малое число шагов).
  4. Сортируем по числу фильмов (убывание) и берём POOL_SIZE.

Результат — два файла в текущей папке:
  start_pool_<POOL_SIZE>.txt — готовый Kotlin `listOf(...)` с комментариями;
  start_pool_<POOL_SIZE>.csv — tmdb_id, имя, число фильмов (для проверки).
"""

import csv
import getpass
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# ============================ НАСТРОЙКИ ============================

# Впишите ключ TMDB сюда (https://www.themoviedb.org/settings/api),
# либо оставьте пустым — скрипт спросит при запуске.
TMDB_API_KEY = ""

POOL_SIZE = 130        # сколько актёров в итоговом списке
PAGES = 5              # страниц /movie/popular (20 фильмов на страницу = 100)
CAST_LIMIT = 15        # сколько актёров брать из состава каждого фильма
MIN_MOVIES = 2         # мин. число фильмов выборки для актёра (пересечение)
SLEEP_S = 0.3          # пауза между запросами (учтивость к TMDB)

# ===================================================================

API = "https://api.themoviedb.org/3"


def load_key() -> str:
    if TMDB_API_KEY:
        return TMDB_API_KEY.strip()
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        if arg in ("-h", "--help"):
            sys.exit(
                "Использование: python generate_start_pool.py [КЛЮЧ]\n"
                "Ключ: константа TMDB_API_KEY в начале скрипта, аргумент\n"
                "или интерактивный запрос при запуске.\n"
                "Нужна сеть с доступом к api.themoviedb.org (VPN)."
            )
        return arg.strip()
    try:
        key = getpass.getpass("TMDB API key: ")
    except (EOFError, KeyboardInterrupt):
        sys.exit("\nКлюч не введён, выход.")
    return key.strip()


def get(key: str, path: str, params: dict) -> dict:
    qs = urllib.parse.urlencode({"api_key": key, "language": "en-US", **params})
    url = f"{API}/{path}?{qs}"
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 401:
                sys.exit("Ключ TMDB отклонён (401). Проверьте TMDB_API_KEY.")
            if e.code == 429:
                wait = int(e.headers.get("Retry-After", "2") or 2)
                print(f"  429 (rate limit) на {path}, ждём {wait}s")
                time.sleep(wait)
                continue
            if 500 <= e.code < 600:
                time.sleep(2 * (attempt + 1))
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"Не удалось получить {path} после 3 попыток "
                       f"(нет сети/VPN?)")


def main() -> None:
    key = load_key()
    total_movies = 20 * PAGES

    print(f"1/3 Собираем {total_movies} популярных фильмов...")
    movies = {}
    for page in range(1, PAGES + 1):
        data = get(key, "movie/popular", {"page": page})
        results = data.get("results") or []
        for m in results:
            if m.get("id"):
                movies[m["id"]] = m
        print(f"  page {page}: +{len(results)}")
        time.sleep(SLEEP_S)
    movies = sorted(movies.values(), key=lambda m: -(m.get("popularity") or 0))
    movies = movies[:total_movies]
    print(f"  всего фильмов: {len(movies)}")

    print(f"2/3 Собираем составы (top-{CAST_LIMIT})...")
    stats = {}  # actor_id -> [name, movies_count]
    for i, m in enumerate(movies, 1):
        credits = get(key, f"movie/{m['id']}/credits", {})
        for c in (credits.get("cast") or [])[:CAST_LIMIT]:
            if not c.get("id"):
                continue
            rec = stats.setdefault(c["id"], [c.get("name") or "?", 0])
            rec[1] += 1
        if i % 20 == 0:
            print(f"  {i}/{len(movies)}")
        time.sleep(SLEEP_S)

    eligible = [(pid, name, cnt) for pid, (name, cnt) in stats.items()
                if cnt >= MIN_MOVIES]
    eligible.sort(key=lambda t: (-t[2], t[0]))
    pool = eligible[:POOL_SIZE]
    if len(pool) < POOL_SIZE:
        print(f"ВНИМАНИЕ: пригодных актёров {len(pool)} < {POOL_SIZE} "
              f"(снизьте MIN_MOVIES или увеличьте PAGES)")

    txt = f"start_pool_{POOL_SIZE}.txt"
    lines = [
        "// Сгенерировано generate_start_pool.py:",
        f"// {len(movies)} популярных фильмов TMDB, "
        f"пересечение составов >= {MIN_MOVIES}.",
        "listOf(",
    ]
    for pid, name, cnt in pool:
        lines.append(f"    {pid}, // {name} ({cnt} фильмов в выборке)")
    lines.append(")")
    with open(txt, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    csv_path = f"start_pool_{POOL_SIZE}.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["tmdb_id", "name", "movies_in_sample"])
        w.writerows(pool)

    print(f"3/3 Готово: {len(pool)} актёров")
    print(f"  {txt}")
    print(f"  {csv_path}")
    print("Топ-10:")
    for pid, name, cnt in pool[:10]:
        print(f"  {pid:>6}  {name}  ({cnt})")


if __name__ == "__main__":
    main()
