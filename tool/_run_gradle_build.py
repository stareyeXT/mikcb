import subprocess, os, sys

project_root = r'C:\daima\zwg\mikcb\mikcb-ECJTU'
android_dir = os.path.join(project_root, 'android')

# locate extracted gradle 9.6.1 distribution
base = r'C:\Users\ME\.gradle\wrapper\dists\gradle-9.6.1-bin'
gradle_bat = None
for d in os.listdir(base):
    cand = os.path.join(base, d, 'gradle-9.6.1', 'bin', 'gradle.bat')
    if os.path.exists(cand):
        gradle_bat = cand
        break

if not gradle_bat:
    print('ERROR: gradle-9.6.1 not extracted')
    sys.exit(2)

print('gradle_bat =', gradle_bat)

# Use JDK 21 (officially supported by Gradle 9.6.1); JDK 24 breaks native-platform.dll load.
jdk21 = r'C:\Users\ME\.jdks\jdk-21.0.11+10'
os.environ['JAVA_HOME'] = jdk21
os.environ['PATH'] = os.path.join(jdk21, 'bin') + os.pathsep + os.environ.get('PATH', '')
print('JAVA_HOME =', jdk21)

log_path = os.path.join(project_root, 'build_release_log.txt')
with open(log_path, 'w', encoding='utf-8') as log:
    cmd = [gradle_bat, '-p', android_dir, ':app:assembleProdRelease']
    p = subprocess.run(cmd, cwd=project_root, stdout=log, stderr=subprocess.STDOUT, shell=True)
    rc = p.returncode

print('GRADLE_RC=', rc)
sys.exit(rc)
