"""
API mínima (0.27.0): busca en el bytecode de TODOS los módulos (también core, domain y licencia, que lint no analiza)
llamadas a java.*/android.* que no existen en Android 8 (minSdk 26), según api-versions.xml del SDK.

Uso (después de `./gradlew :app:assembleDebug spviTests`):
    python3 tools/verificacion/api_minima.py
Sale con 1 si encuentra llamadas no permitidas. Las de app/data/designsystem protegidas con `SDK_INT` las acepta
la lista PERMITIDAS de abajo (lint ya comprueba allí la protección); en core/domain/licencia no se permite ninguna.
"""
import subprocess,re,sys,glob,os,xml.etree.ElementTree as ET
SDKH=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or sys.exit('Falta ANDROID_HOME')
SDK=os.path.join(SDKH,'platforms','android-35','data','api-versions.xml')
JH=os.environ.get('JAVA_HOME')
JAVAP=os.path.join(JH,'bin','javap') if JH else 'javap'
MIN=26
RAIZ=os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIRS={'core':'Android/core/build/classes/kotlin/main','domain':'Android/domain/build/classes/kotlin/main','licencia':'Android/licencia/build/classes/kotlin/main',
      'data':'Android/data/build/tmp/kotlin-classes/debug','designsystem':'Android/designsystem/build/tmp/kotlin-classes/debug','app':'Android/app/build/tmp/kotlin-classes/debug'}
JVM={'core','domain','licencia'}
# Llamadas revisadas a mano: protegidas con Build.VERSION.SDK_INT (lint NewApi las da por buenas).
PERMITIDAS={
 'android/content/Intent.getParcelableExtra','android/content/pm/PackageInstaller$SessionParams.setRequireUserAction',
 'android/view/Window.setNavigationBarContrastEnforced','android/provider/MediaStore$Downloads.getContentUri',
 'android/content/pm/PackageInfo.getLongVersionCode','android/content/pm/PackageInfo.F:signingInfo',
 'android/content/pm/SigningInfo.hasMultipleSigners','android/content/pm/SigningInfo.getApkContentsSigners',
 'android/content/pm/SigningInfo.getSigningCertificateHistory','android/graphics/Typeface.create',
 'android/security/keystore/KeyGenParameterSpec$Builder.setIsStrongBoxBacked'}
root=ET.parse(SDK).getroot()
api={}
for c in root.findall('class'):
    cs=int(c.get('since','1')); name=c.get('name')
    ms={}
    for m in c.findall('method'):
        ms[m.get('name')]=int(m.get('since',cs))
    for f in c.findall('field'):
        ms['F:'+f.get('name')]=int(f.get('since',cs))
    supers=[e.get('name') for e in c.findall('extends')+c.findall('implements')]
    api[name]=(cs,ms,supers)
def since(cls,sig,seen=None):
    if cls not in api: return None
    cs,ms,sup=api[cls]
    if sig in ms: return max(cs,ms[sig]) if False else ms[sig]
    for s in sup:
        r=since(s,sig)
        if r is not None: return r
    return None
def modulo(f):
    for m,d in DIRS.items():
        if f.startswith(os.path.join(RAIZ,d)): return m
faltan=[m for m,d in DIRS.items() if not os.path.isdir(os.path.join(RAIZ,d))]
if faltan: sys.exit('Compila primero (faltan clases de: '+', '.join(faltan)+')')
porMod={m:glob.glob(os.path.join(RAIZ,d)+'/**/*.class',recursive=True) for m,d in DIRS.items()}
classes=[f for v in porMod.values() for f in v]
lotes=[(m,v[i:i+200]) for m,v in porMod.items() for i in range(0,len(v),200)]
bad={}
for mod,lote in lotes:
    out=subprocess.run([JAVAP,'-c','-p']+lote,capture_output=True,text=True).stdout
    cur=None
    for line in out.split('\n'):
        m=re.match(r'(?:public |private |protected |final |abstract |static )*(?:class|interface) (\S+)',line.strip())
        if line and not line.startswith(' ') and m: cur=m.group(1)
        m=re.search(r'// (Method|InterfaceMethod|Field) ((?:java|javax|android|org/json|org/xml)/[\w/$]+)\.("?[\w<>$]+"?):(\S+)',line)
        if not m: continue
        kind,cls,name,desc=m.groups(); name=name.strip('"')
        sig=('F:'+name) if kind=='Field' else name+desc
        if cls not in api:
            continue
        s=since(cls,sig)
        if s is None: s=api[cls][0]
        s=max(s,api[cls][0])
        if s<=MIN: continue
        clave=cls+'.'+(sig if sig.startswith('F:') else name)
        if mod not in JVM and clave in PERMITIDAS: continue
        bad.setdefault((cls,sig,s),set()).add(f'{mod}:{cur}')
for (c,s,v),w in sorted(bad.items(),key=lambda x:-x[0][2]):
    print(f"API {v}: {c}.{s}  <- {', '.join(sorted(w))[:300]}")
print(f"API mínima {MIN}: {len(classes)} clases revisadas, {len(bad)} llamadas no permitidas")
sys.exit(1 if bad else 0)
