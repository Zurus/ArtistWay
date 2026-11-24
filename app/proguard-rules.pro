# Ревью-правила R8/ProGuard (docs/05_roadmap.md, Этап 6, пункт 1).
#
# Стек: Retrofit + OkHttp + kotlinx.serialization (DTO), Room (сущности/DAO),
# Hilt/Dagger, Coil. Большинство библиотек несут свои consumer-правила;
# здесь — дополнения и dontwarn для транзитивных предупреждений.

# ===== Общие атрибуты, нужные Retrofit/Room/serializer =====================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations,
    RuntimeVisibleTypeAnnotations, AnnotationDefault
-keepattributes Exceptions
-keepattributes *Annotation*

# ===== Retrofit =============================================================
# Интерфейсы API теряются, если R8 решит, что «у класса нет реализации»:
# держим методы с HTTP-аннотациями у сокращаемого (allowshrinking) интерфейса.
-dontwarn retrofit2.**
-keepclassmembers,allowshrinking interface * {
    @retrofit2.http.* <methods>;
}
# Тело/заголовки/поля читаетесь через рефлексию по аннотациям.
-keepclassmembers class * {
    @retrofit2.http.Body <methods>;
    @retrofit2.http.Field <methods>;
    @retrofit2.http.FieldMap <methods>;
    @retrofit2.http.FormUrlEncoded <methods>;
    @retrofit2.http.Header <methods>;
    @retrofit2.http.HeaderMap <methods>;
    @retrofit2.http.Path <methods>;
    @retrofit2.http.Query <methods>;
    @retrofit2.http.QueryMap <methods>;
    @retrofit2.http.Streaming <methods>;
    @retrofit2.http.Url <methods>;
}

# ===== OkHttp / Okio ========================================================
# Внутренние интерцепторы/коллбеки создаются по имени конструктора.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class okhttp3.internal.** {
    <init>(okhttp3.OkHttpClient, boolean);
}
-keepclassmembers @okhttp3.internal.Beta class okhttp3.** {
    <init>(okhttp3.Call$Factory, boolean);
}
# Kotlin + OkHttp: metadata.
-keepnames @okhttp3.internal.reflect.Cloneable class *
-keepclassmembers class * {
    @okhttp3.internal.reflect.AccessorGetterMethod <methods>;
    @okhttp3.internal.reflect.AccessorSetterMethod <methods>;
}
-dontwarn java.lang.instrument.**

# ===== Room =================================================================
# Сгенерированный код ссылается на сущности/DAO напрямую; keep-правила —
# страховка от переименования @Entity-полей и TypeConverter-классов.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.TypeConverter class * {
    <fields>;
    <methods>;
}

# ===== kotlinx.serialization ================================================
# Генератор плагина создаёт $$serializer; R8 должен их сохранить и не
# переименовывать поля DTO (оригинальные имена приходят из TMDB JSON).
-keep,includedescriptorclasses class com.zurus.kinosvyazi.data.remote.dto.**$$serializer {
    *;
}
-keepclassmembers class com.zurus.kinosvyazi.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.zurus.kinosvyazi.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.serialization.**

# ===== Hilt / Dagger ========================================================
-keepattributes DefaultMethods, MethodParameters
-dontwarn dagger.hilt.**
-keepclassmembers @dagger.hilt.android.lifecycle.HiltViewModel class * {
    @javax.inject.Inject <init>(...);
}

# ===== Coil =================================================================
-keep class coil3.** { *; }
-dontwarn coil3.**
