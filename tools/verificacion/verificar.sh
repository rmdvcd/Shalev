#!/usr/bin/env bash
# =====================================================================================================================
# Shalev — verificación SIN Gradle ni Android SDK (Linux x86-64, Python 3, curl, unzip; ~1,5 GB de disco, 2 GB de RAM).
#
# NO sustituye a `./gradlew` (no hay AGP, aapt2, lint, R8 ni APK). Sirve cuando no hay Android Studio a mano.
# Hace lo siguiente:
#   1. Descarga JDK 17, kotlinc 2.0.21, KSP 2.0.21-1.0.28 y las librerías del catálogo (Maven Central / Google Maven).
#   2. Compila TODOS los módulos (core, licencia, domain, data, designsystem, app) con los plugins de Compose y de
#      kotlinx-serialization, usando el framework de Robolectric (android-all 14) como android.jar.
#   3. Ejecuta Room (KSP): valida cada @Query, genera los DAO y exporta data/schemas/…/<VERSION>.json.
#   4. Ejecuta Hilt/Dagger (KSP) en data y app: valida el grafo de inyección completo (faltas de binding = error).
#   5. Comprueba que la última migración deja exactamente el esquema exportado (SQLite + PRAGMA, como Room).
#   6. Ejecuta los tests JVM de core, licencia, domain, data, designsystem y app (no las capturas Roborazzi).
#   7. Compila (sin ejecutar) las capturas Roborazzi y los tests instrumentados.
#
# Uso:  bash tools/verificacion/verificar.sh            (TC=<dir> para elegir la caché; por defecto ~/.cache/spvi-tc)
# =====================================================================================================================
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TC="${TC:-$HOME/.cache/spvi-tc}"; mkdir -p "$TC"; TC="$(cd "$TC" && pwd -P)"
OUT="$TC/out"; rm -rf "$OUT"; mkdir -p "$OUT"
M=https://repo1.maven.org/maven2; G=https://dl.google.com/android/maven2
paso() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
falla() { printf '\033[31mFALLO: %s\033[0m\n' "$*"; exit 1; }
bajar() { [ -s "$2" ] || curl -fsSL -o "$2" "$1" || falla "descarga $1"; }

# --------------------------------------------------------------------------------------------- 1. herramientas
paso "1. Herramientas (se reutilizan si ya están en $TC)"
if [ ! -x "$TC/jdk/bin/java" ]; then
  bajar "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" "$TC/jdk.tgz"
  mkdir -p "$TC/jdk" && tar xzf "$TC/jdk.tgz" -C "$TC/jdk" --strip-components=1 && rm "$TC/jdk.tgz"
fi
export JAVA_HOME="$TC/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -x "$TC/kotlinc/bin/kotlinc" ]; then
  bajar "https://github.com/JetBrains/kotlin/releases/download/v2.0.21/kotlin-compiler-2.0.21.zip" "$TC/kc.zip"
  unzip -q "$TC/kc.zip" -d "$TC" && rm "$TC/kc.zip"
fi
K="$TC/kotlinc"; STD="$K/lib/kotlin-stdlib.jar"; SER="$K/lib/kotlinx-serialization-compiler-plugin.jar"
mkdir -p "$TC/ksp"
bajar "$M/org/jetbrains/kotlin/kotlin-compose-compiler-plugin/2.0.21/kotlin-compose-compiler-plugin-2.0.21.jar" "$TC/compose-plugin.jar"
for a in symbol-processing symbol-processing-api symbol-processing-common-deps; do
  bajar "$M/com/google/devtools/ksp/$a/2.0.21-1.0.28/$a-2.0.21-1.0.28.jar" "$TC/ksp/$a.jar"; done
for x in kotlin-compiler-embeddable/2.0.21 kotlin-daemon-embeddable/2.0.21 kotlin-script-runtime/2.0.21 kotlin-reflect/1.6.10 \
         kotlin-serialization-compiler-plugin-embeddable/2.0.21; do
  n=${x%/*}; v=${x#*/}; bajar "$M/org/jetbrains/kotlin/$n/$v/$n-$v.jar" "$TC/ksp/$n.jar"; done
bajar "$M/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar" "$TC/ksp/trove4j.jar"
bajar "$M/org/jetbrains/annotations/13.0/annotations-13.0.jar" "$TC/ksp/annotations.jar"
bajar "$M/org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar" "$TC/android-all.jar"

resolver() { # nombre, raíces…
  local d="$TC/lib/$1"; shift
  if [ ! -f "$d/.ok" ]; then rm -rf "$d"; mkdir -p "$d"; printf '%s\n' "$@" > "$d/raices.txt"
    python3 "$ROOT/tools/verificacion/resolver_maven.py" "$d/raices.txt" "$d" >/dev/null 2>&1 || falla "resolver $d"; touch "$d/.ok"; fi
  ls "$d"/*.jar | tr '\n' ':'
}
JVM=$(resolver jvm org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0 org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0 \
  org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3 javax.inject:javax.inject:1 junit:junit:4.13.2)
DATA=$(resolver data androidx.room:room-runtime:2.6.1 androidx.room:room-ktx:2.6.1 androidx.datastore:datastore-preferences:1.1.1 \
  net.zetetic:sqlcipher-android:4.6.1 com.google.dagger:hilt-android:2.52 com.squareup.okhttp3:okhttp:4.12.0 \
  com.squareup.okhttp3:mockwebserver:4.12.0 org.dhatim:fastexcel:0.18.4 androidx.exifinterface:exifinterface:1.3.7 \
  androidx.annotation:annotation:1.9.1 org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0)
APP=$(resolver app androidx.compose.ui:ui:1.7.6 androidx.compose.foundation:foundation:1.7.6 androidx.compose.material3:material3:1.3.1 \
  androidx.compose.ui:ui-tooling-preview:1.7.6 androidx.core:core-ktx:1.15.0 androidx.activity:activity-compose:1.9.3 \
  androidx.lifecycle:lifecycle-runtime-compose:2.8.7 androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7 \
  androidx.navigation:navigation-compose:2.8.5 androidx.core:core-splashscreen:1.0.1 androidx.camera:camera-core:1.4.1 \
  androidx.camera:camera-camera2:1.4.1 androidx.camera:camera-lifecycle:1.4.1 androidx.camera:camera-view:1.4.1 \
  com.google.mlkit:barcode-scanning:17.3.0 com.google.mlkit:text-recognition:16.0.1 io.coil-kt.coil3:coil-compose:3.0.4 androidx.hilt:hilt-navigation-compose:1.2.0 \
  com.google.zxing:core:3.5.3 androidx.biometric:biometric:1.1.0 androidx.fragment:fragment-ktx:1.8.5 \
  androidx.lifecycle:lifecycle-process:2.8.7 androidx.profileinstaller:profileinstaller:1.4.1 \
  androidx.room:room-runtime:2.6.1 androidx.room:room-ktx:2.6.1 androidx.datastore:datastore-preferences:1.1.1 \
  net.zetetic:sqlcipher-android:4.6.1 com.google.dagger:hilt-android:2.52 com.squareup.okhttp3:okhttp:4.12.0 \
  org.dhatim:fastexcel:0.18.4 androidx.exifinterface:exifinterface:1.3.7)
PRUEBAS=$(resolver pruebas org.robolectric:robolectric:4.14.1 io.github.takahirom.roborazzi:roborazzi:1.38.0 \
  io.github.takahirom.roborazzi:roborazzi-compose:1.38.0 io.github.takahirom.roborazzi:roborazzi-junit-rule:1.38.0 \
  androidx.compose.ui:ui-test-junit4:1.7.6 androidx.compose.ui:ui-test-manifest:1.7.6 androidx.test:core:1.6.1 \
  androidx.test.ext:junit:1.2.1 androidx.room:room-testing:2.6.1 androidx.test:runner:1.6.2)
resolver room androidx.room:room-compiler:2.6.1 >/dev/null
resolver hilt com.google.dagger:hilt-compiler:2.52 >/dev/null
rm -f "$TC"/lib/{room,hilt}/*symbol-processing-api* "$TC"/lib/pruebas/*nativeruntime* "$TC"/lib/pruebas/*android-all*
PRUEBAS=$(ls "$TC"/lib/pruebas/*.jar | tr '\n' ':')

# R / BuildConfig mínimos (AGP los genera; aquí solo hacen falta para compilar).
mkdir -p "$TC/stub/src/cu/shalev/app" "$TC/stub/src/cu/shalev/designsystem" "$TC/stub/rt/android/os"
VN=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' "$ROOT/app/build.gradle.kts" | head -1)
VC=$(sed -n 's/.*versionCode *= *\([0-9]*\).*/\1/p' "$ROOT/app/build.gradle.kts" | head -1)
cat > "$TC/stub/src/cu/shalev/app/R.java" <<'J'
package cu.shalev.app; public final class R { public static final class drawable { public static final int ic_stat_shalev = 0x7f010002; public static final int billete_1 = 0x7f010010; public static final int billete_3 = 0x7f010011; public static final int billete_5 = 0x7f010012; public static final int billete_10 = 0x7f010013; public static final int billete_20 = 0x7f010014; public static final int billete_50 = 0x7f010015; public static final int billete_100 = 0x7f010016; public static final int billete_200 = 0x7f010017; public static final int billete_500 = 0x7f010018; public static final int billete_1000 = 0x7f010019; public static final int billete_2000 = 0x7f01001a; public static final int billete_5000 = 0x7f01001b; public static final int billete_20000 = 0x7f01001c; } }
J
cat > "$TC/stub/src/cu/shalev/app/BuildConfig.java" <<J
package cu.shalev.app; public final class BuildConfig { public static final boolean DEBUG = false; public static final int VERSION_CODE = ${VC:-1}; public static final String VERSION_NAME = "${VN:-0}"; public static final String APPLICATION_ID = "cu.shalev.app"; public static final String GITHUB_REPO = ""; }
J
cat > "$TC/stub/src/cu/shalev/designsystem/R.java" <<'J'
package cu.shalev.designsystem; public final class R { public static final class drawable { public static final int shalev_logo = 0x7f010001; } public static final class font { public static final int orbitron = 0x7f020001; } }
J
# Solo para EJECUTAR tests JVM: android.os.Build/SystemProperties sin código nativo (Gradle usa un android.jar con stubs).
cat > "$TC/stub/rt/android/os/SystemProperties.java" <<'J'
package android.os; public class SystemProperties { public static String get(String k){return "";} public static String get(String k,String d){return d;}
 public static int getInt(String k,int d){return d;} public static long getLong(String k,long d){return d;} public static boolean getBoolean(String k,boolean d){return d;}
 public static void set(String k,String v){} public static void addChangeCallback(Runnable r){} }
J
cat > "$TC/stub/rt/android/os/Build.java" <<'J'
package android.os; public class Build { public static final String UNKNOWN="unknown", ID="x", DISPLAY="x", PRODUCT="robolectric", DEVICE="robolectric", BOARD="x",
 MANUFACTURER="robolectric", BRAND="robolectric", MODEL="robolectric", HARDWARE="x", FINGERPRINT="x", TYPE="user", TAGS="x", SERIAL="x", BOOTLOADER="x", HOST="x", USER="x";
 public static final String[] SUPPORTED_ABIS={"x86_64"}; public static final long TIME=0L; public static final boolean IS_DEBUGGABLE=false, IS_EMULATOR=true;
 public static class VERSION { public static final int SDK_INT=34, PREVIEW_SDK_INT=0; public static final String RELEASE="14", CODENAME="REL", INCREMENTAL="x", SDK="34"; }
 public static class VERSION_CODES {} }
J
javac -nowarn -d "$TC/stub/cls" $(find "$TC/stub/src" -name '*.java')
javac -nowarn -d "$TC/stub/rtcls" $(find "$TC/stub/rt" -name '*.java')

ANDROID="$TC/android-all.jar"
kc() { # salida, classpath, fuentes… (EXTRA = opciones adicionales; COMPOSE=1 activa el plugin de Compose)
  local o=$1 cp=$2; shift 2
  local log; log=$(JAVA_OPTS="-Xmx1400m -XX:+UseSerialGC" "$K/bin/kotlinc" -jvm-target 11 -nowarn ${COMPOSE:+-Xplugin="$TC/compose-plugin.jar"} \
    -Xplugin="$SER" -opt-in=kotlin.RequiresOptIn ${EXTRA:-} -cp "$cp" -d "$o" "$@" 2>&1 | grep -v '^warning' || true)
  if [ -n "$log" ]; then echo "$log" | head -60; falla "compilación → $o"; fi
}
pruebas() { # dir-clases, classpath, fuentes-de-test… → ejecuta las clases con @Test
  local od=$1 cp=$2; shift 2
  local cls; cls=$(python3 - "$od" $(find "$@" -name '*.kt' -not -path '*/capturas/*') <<'PY'
import re, sys, os
od, out = sys.argv[1], set()
for f in sys.argv[2:]:
    s = open(f, encoding="utf-8").read()
    if "@Test" not in s: continue
    pk = re.search(r"^package\s+([\w.]+)", s, re.M).group(1)
    decl = [(m.start(), m.group(1)) for m in re.finditer(r"^(?:internal |open |abstract )?class (\w+)", s, re.M)]
    for i, (pos, c) in enumerate(decl):
        fin = decl[i + 1][0] if i + 1 < len(decl) else len(s)
        if "@Test" in s[pos:fin] and "abstract class" not in s[pos:pos + 20] and os.path.exists(f"{od}/{pk.replace('.', '/')}/{c}.class"):
            out.add(f"{pk}.{c}")
print(" ".join(sorted(out)))
PY
)
  local r; r=$(java -Xmx900m -cp "$TC/stub/rtcls:$od:$cp:$STD" org.junit.runner.JUnitCore $cls 2>&1 | grep -vE '^\s+at ' || true)
  echo "$r" | grep -E '^OK \(|^Tests run' || true
  echo "$r" | grep -q '^OK (' || { echo "$r" | grep -E '^[0-9]+\) ' -A2 | head -40; falla "tests $od"; }
}
ksp() { # nombre, dir-procesadores, classpath, "opciones -P extra", fuentes…
  local n=$1 pd=$2 cp=$3 xo=$4; shift 4
  local P="plugin:com.google.devtools.ksp.symbol-processing" O="$OUT/ksp-$n"; mkdir -p "$O"
  local AP; AP=$(ls "$pd"/*.jar | tr '\n' ':'); AP=${AP%:}
  local KXS; KXS=$(ls "$TC"/lib/jvm/*kotlinx-serialization-*.jar | tr '\n' ','); KXS=${KXS%,}
  local CCP="$TC/ksp/kotlin-compiler-embeddable.jar:$STD:$TC/ksp/kotlin-script-runtime.jar:$TC/ksp/kotlin-reflect.jar:$TC/ksp/trove4j.jar:$TC/ksp/kotlin-daemon-embeddable.jar:$TC/ksp/annotations.jar:$JVM"
  java -Xmx1300m -XX:+UseSerialGC -cp "$CCP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -language-version 1.9 -api-version 1.9 \
    -jvm-target 11 -nowarn -Xplugin="$TC/ksp/symbol-processing.jar,$TC/ksp/symbol-processing-api.jar,$TC/ksp/symbol-processing-common-deps.jar,$KXS" \
    -Xplugin="$TC/ksp/kotlin-serialization-compiler-plugin-embeddable.jar" \
    -P "$P:apclasspath=$AP" -P "$P:projectBaseDir=$O" -P "$P:classOutputDir=$O/cls" -P "$P:javaOutputDir=$O/java" \
    -P "$P:kotlinOutputDir=$O/kotlin" -P "$P:resourceOutputDir=$O/res" -P "$P:kspOutputDir=$O/ksp" -P "$P:cachesDir=$O/cache" \
    -P "$P:incremental=false" $xo -cp "$cp:$STD" -d "$O/out" "$@" > "$O/log.txt" 2>&1 || true
  if grep -qE '^(e: |error:|exception:)' "$O/log.txt"; then grep -E '^(e: |error:|exception:)' -A3 "$O/log.txt" | head -40; falla "KSP $n"; fi
}

S="$ROOT"
# --------------------------------------------------------------------------------------------- 2. compilación
paso "2. Compilación de los módulos"
kc "$OUT/core" "$JVM" "$S/core/src/main" "$S/core/src/test"
kc "$OUT/licencia" "$JVM:$OUT/core" "$S/licencia/src/main" "$S/licencia/src/test"
kc "$OUT/domain" "$JVM:$OUT/core:$OUT/licencia" "$S/domain/src/main" "$S/domain/src/test"
BASE="$JVM:$OUT/core:$OUT/licencia:$OUT/domain:$DATA$ANDROID"
paso "3. Room (KSP): consultas, DAO y esquema"
SCH="$OUT/schemas"; mkdir -p "$SCH"
PK="plugin:com.google.devtools.ksp.symbol-processing"
ksp room "$TC/lib/room" "$BASE" "-P $PK:apoption=room.schemaLocation=$SCH -P $PK:apoption=room.generateKotlin=true -P $PK:apoption=room.incremental=false" "$S/data/src/main/kotlin"
kc "$OUT/data" "$BASE" "$S/data/src/main/kotlin" "$OUT/ksp-room/kotlin"
VER=$(sed -n 's/.*const val VERSION *= *\([0-9]*\).*/\1/p' "$S/data/src/main/kotlin/cu/shalev/data/db/ShalevDatabase.kt" | head -1)
NUEVO="$SCH/cu.shalev.data.db.ShalevDatabase/$VER.json"; VIEJO="$S/data/schemas/cu.shalev.data.db.ShalevDatabase/$VER.json"
[ -f "$NUEVO" ] || falla "Room no exportó $VER.json"
if [ -f "$VIEJO" ]; then cmp -s "$NUEVO" "$VIEJO" && echo "Esquema $VER.json sin cambios." || { echo "AVISO: las entidades no coinciden con $VER.json versionado (¿falta subir la versión?). Nuevo: $NUEVO"; }
else mkdir -p "$(dirname "$VIEJO")"; cp "$NUEVO" "$VIEJO"; echo "Exportado $VIEJO"; fi
paso "4. Hilt/Dagger (KSP): grafo de inyección"
HO="-P $PK:apoption=dagger.hilt.android.internal.disableAndroidSuperclassValidation=true"
ksp hilt-data "$TC/lib/hilt" "$BASE:$OUT/data" "$HO" "$S/data/src/main/kotlin" "$OUT/ksp-room/kotlin"
mkdir -p "$OUT/hilt-data"; javac -nowarn -proc:none -cp "$OUT/data:$BASE:$STD" -d "$OUT/hilt-data" $(find "$OUT/ksp-hilt-data/java" -name '*.java') || falla "javac Hilt (data)"
# app+data resueltos JUNTOS (una sola versión por librería: p. ej. hilt-android arrastra androidx.activity 1.5 y dagger).
UI="$JVM:$OUT/core:$OUT/licencia:$OUT/domain:$APP$ANDROID"
COMPOSE=1 kc "$OUT/designsystem" "$UI:$TC/stub/cls" "$S/designsystem/src/main/kotlin"
APPCP="$UI:$OUT/data:$OUT/designsystem:$TC/stub/cls"
COMPOSE=1 kc "$OUT/app" "$APPCP" "$S/app/src/main/kotlin"
ksp hilt-app "$TC/lib/hilt" "$OUT/hilt-data:$APPCP" "$HO" "$S/app/src/main/kotlin"
ls "$OUT"/ksp-hilt-app/java/cu/shalev/app/DaggerShalevApplication_HiltComponents_SingletonC.java >/dev/null 2>&1 || falla "Hilt no generó el componente"
mkdir -p "$OUT/hilt-app"; javac -nowarn -proc:none -cp "$OUT/app:$OUT/hilt-data:$APPCP:$STD" -d "$OUT/hilt-app" $(find "$OUT/ksp-hilt-app/java" -name '*.java') || falla "javac Hilt (app)"
echo "Grafo de Hilt completo y compilado."

# --------------------------------------------------------------------------------------------- 5. migración
paso "5. Última migración == esquema exportado"
python3 - "$NUEVO" "$S/data/src/main/kotlin/cu/shalev/data/db/ShalevDatabase.kt" "$VER" <<'PY'
import json, sqlite3, re, sys
esq, src, ver = sys.argv[1], open(sys.argv[2]).read(), int(sys.argv[3])
ents = {e["tableName"]: e for e in json.load(open(esq))["database"]["entities"]}
m = re.search(r"internal val SQL_%d_%d = listOf\((.*?)\n\s*\)\n" % (ver - 1, ver), src, re.S)
if not m: print("Sin SQL_%d_%d: se omite." % (ver - 1, ver)); sys.exit(0)
stmts, cur = [], ""
for t in re.finditer(r'"((?:[^"\\]|\\.)*)"(\s*\+)?', m.group(1)):
    cur += t.group(1)
    if not t.group(2): stmts.append(cur); cur = ""
nuevas = set(); tablas_nuevas = set()
for s in stmts:
    a = re.match(r"ALTER TABLE `(\w+)` ADD COLUMN `(\w+)`", s)
    if a: nuevas.add(a.groups())
    c = re.match(r"CREATE TABLE IF NOT EXISTS `(\w+)`", s)
    if c: tablas_nuevas.add(c.group(1))
def crear(db, previa):
    for n, e in ents.items():
        if previa and n in tablas_nuevas: continue
        sql = e["createSql"].replace("${TABLE_NAME}", n)
        if previa:
            for (t, col) in nuevas:
                if t == n: sql = re.sub(r",\s*`%s` [^,)]*" % col, "", sql)
        db.execute(sql)
        for i in e.get("indices", []):
            if previa and any((n, c) in nuevas for c in i["columnNames"]): continue
            db.execute(i["createSql"].replace("${TABLE_NAME}", n))
def info(db):
    r = {}
    for (n,) in db.execute("select name from sqlite_master where type='table' and name not like 'sqlite_%'"):
        r[n] = (sorted((c[1], c[2].upper(), c[3], c[4], c[5]) for c in db.execute(f"PRAGMA table_info(`{n}`)")),
                sorted((i[1], i[2]) for i in db.execute(f"PRAGMA index_list(`{n}`)") if not i[1].startswith("sqlite_")),
                sorted(tuple(f[2:]) for f in db.execute(f"PRAGMA foreign_key_list(`{n}`)")))
    return r
a = sqlite3.connect(":memory:"); crear(a, False)
b = sqlite3.connect(":memory:"); crear(b, True)
for s in stmts: b.execute(s)
d = [n for n in set(info(a)) | set(info(b)) if info(a).get(n) != info(b).get(n)]
if d: print("Tablas distintas tras migrar:", d); sys.exit(1)
print("MIGRACION_%d_%d deja el esquema de %d.json (%d sentencias)." % (ver - 1, ver, ver, len(stmts)))
PY

# --------------------------------------------------------------------------------------------- 6–7. tests
paso "6. Tests JVM"
echo "core:"; pruebas "$OUT/core" "$JVM" "$S/core/src/test"
echo "licencia:"; pruebas "$OUT/licencia" "$OUT/core:$JVM" "$S/licencia/src/test"
echo "domain:"; pruebas "$OUT/domain" "$OUT/core:$OUT/licencia:$JVM" "$S/domain/src/test"
EXTRA="-Xfriend-paths=$OUT/data" kc "$OUT/data-test" "$BASE:$OUT/data" "$S/data/src/test/kotlin"
echo "data:"; pruebas "$OUT/data-test" "$OUT/data:$BASE" "$S/data/src/test/kotlin"
COMPOSE=1 EXTRA="-Xfriend-paths=$OUT/designsystem" kc "$OUT/ds-test" "$APPCP" "$S/designsystem/src/test/kotlin"
echo "designsystem:"; pruebas "$OUT/ds-test" "$APPCP" "$S/designsystem/src/test/kotlin"
TESTS=$(find "$S/app/src/test/kotlin" -name '*.kt' -not -path '*/capturas/*')
COMPOSE=1 EXTRA="-Xfriend-paths=$OUT/app" kc "$OUT/app-test" "$APPCP:$OUT/app" $TESTS "$S/app/src/sharedTest/kotlin"
echo "app:"; pruebas "$OUT/app-test" "$APPCP:$OUT/app" "$S/app/src/test/kotlin"
paso "7. Compilación de capturas Roborazzi y tests instrumentados"
COMPOSE=1 EXTRA="-Xfriend-paths=$OUT/app" kc "$OUT/capturas" "$APPCP:$OUT/app:$OUT/app-test:$PRUEBAS" "$S/app/src/test/kotlin/cu/shalev/app/capturas"
COMPOSE=1 EXTRA="-Xfriend-paths=$OUT/app" kc "$OUT/app-at" "$APPCP:$OUT/app:$PRUEBAS" "$S/app/src/androidTest/kotlin" "$S/app/src/sharedTest/kotlin"
EXTRA="-Xfriend-paths=$OUT/data" kc "$OUT/data-at" "$OUT/data:$BASE:$PRUEBAS" "$S/data/src/androidTest/kotlin"

# --------------------------------------------------------------------------------------------- 8. Room en la JVM
# 0.21.9 (P59): los tests instrumentados de :data que usan Room EN MEMORIA se ejecutan aquí con SQLite real por JDBC
# (tools/verificacion/jvm-room: adaptador SupportSQLite → sqlite-jdbc delante de Room en el classpath). No cambia el
# proyecto Gradle: en el teléfono siguen corriendo con el SQLite de Android. Quedan fuera los que necesitan Keystore,
# Instrumentation o assets (EsquemaTest → su versión JVM MigracionesJvmTest, licencia/*, security/*, ConfiguracionInicial).
paso "8. Room en la JVM: tests instrumentados de :data (SQLite por JDBC) y migraciones 4→7"
JDBC=$(resolver jdbc org.xerial:sqlite-jdbc:3.46.1.3 org.slf4j:slf4j-api:1.7.36 org.slf4j:slf4j-nop:1.7.36)
JR="$OUT/jvm-room"; mkdir -p "$JR/cls"
javac -encoding UTF-8 -nowarn -d "$JR/cls" -cp "$ANDROID:$DATA$JVM" $(find "$S/tools/verificacion/jvm-room/src" -name '*.java') || falla "adaptador Room-JDBC"
EXTRA="-Xfriend-paths=$OUT/data" kc "$JR/test" "$JR/cls:$OUT/data:$BASE" "$S/tools/verificacion/jvm-room/test"
ROOMCLS=$(python3 - "$S/data/src/androidTest/kotlin" <<'PY'
import os, re, sys
out = []
for raiz, _, fs in os.walk(sys.argv[1]):
    for f in fs:
        if not f.endswith(".kt"): continue
        s = open(os.path.join(raiz, f), encoding="utf-8").read()
        if "@Test" in s and "Room.inMemoryDatabaseBuilder" in s and not re.search(r"InstrumentationRegistry|KeystoreAead|MigrationTestHelper", s):
            out.append(re.search(r"^package\s+([\w.]+)", s, re.M).group(1) + "." + f[:-3])
print(" ".join(sorted(out)))
PY
)
echo "Clases: $(echo $ROOMCLS | wc -w) de data/src/androidTest + MigracionesJvmTest"
R=$(java -Xmx700m -Dshalev.esquemas="$S/data/schemas/cu.shalev.data.db.ShalevDatabase" -Djava.io.tmpdir="$JR" \
  -cp "$TC/stub/rtcls:$JR/cls:$JDBC$JR/test:$OUT/data-at:$OUT/data:$BASE:$PRUEBAS$STD" org.junit.runner.JUnitCore $ROOMCLS cu.shalev.verificacion.room.MigracionesJvmTest 2>&1 | grep -vE '^\s+at ' || true)
echo "$R" | grep -E '^OK \(|^Tests run' || true
echo "$R" | grep -q '^OK (' || { echo "$R" | grep -E '^[0-9]+\) ' -A2 | head -40; falla "Room en la JVM"; }
paso "9. Permisos del manifiesto de :app dentro de la lista autorizada (shalevPermisos)"
python3 - "$S" <<'PY' || falla "permisos"
import re, sys
s = sys.argv[1]
man = open(f"{s}/app/src/main/AndroidManifest.xml", encoding="utf-8").read()
man = re.sub(r"<!--.*?-->", "", man, flags=re.S)
pedidos = {m.group(2) for m in re.finditer(r'<uses-permission\b([^>]*?)android:name="([^"]+)"([^>]*)>', man)
           if 'tools:node="remove"' not in m.group(0)}
gr = open(f"{s}/build.gradle.kts", encoding="utf-8").read()
bloque = re.search(r"permitidos\.set\(setOf\((.*?)\)\)", gr, re.S).group(1)
permitidos = set(re.findall(r'"([^"]+)"', bloque))
sobran = pedidos - permitidos
print("Pedidos:", ", ".join(sorted(p.rsplit(".", 1)[-1] for p in pedidos)))
if sobran: print("No autorizados:", ", ".join(sorted(sobran))); sys.exit(1)
PY
paso "Todo correcto"
