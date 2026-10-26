#!/usr/bin/env bash
set -euo pipefail
mkdir -p /workspace/.android-tools/downloads /workspace/.android-tools/user /workspace/.android-tools/home
cd /workspace/.android-tools/downloads
if [ ! -x /workspace/.android-tools/jdk/jdk-21.0.8+9/bin/javac ]; then
  curl -fsSL 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz.sha256.txt' -o jdk.sha256
  curl -fsSL 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' -o jdk.tar.gz
  python3 - <<'JDK'
from pathlib import Path
import hashlib, tarfile
assert hashlib.sha256(Path('jdk.tar.gz').read_bytes()).hexdigest() == Path('jdk.sha256').read_text().split()[0]
with tarfile.open('jdk.tar.gz') as t:
    t.extractall('/workspace/.android-tools/jdk', filter='data')
JDK
fi
export JAVA_HOME=/workspace/.android-tools/jdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
# Trust the environment's provided CA certificates; keep TLS validation enabled.
cp "$JAVA_HOME/lib/security/cacerts" /workspace/.android-tools/cacerts
python3 - <<'TRUST'
from pathlib import Path
import os, subprocess, urllib.parse
for cert in Path('/usr/local/share/ca-certificates').glob('*.crt'):
    subprocess.run(['keytool','-importcert','-noprompt','-keystore','/workspace/.android-tools/cacerts','-storepass','changeit','-alias','cloud-'+cert.stem,'-file',str(cert)], check=True, capture_output=True)
opts = '-Djavax.net.ssl.trustStore=/workspace/.android-tools/cacerts'
proxy = urllib.parse.urlparse(os.environ.get('HTTPS_PROXY',''))
if proxy.hostname:
    assert not proxy.username and not proxy.password, 'Use platform-provided proxy authentication'
    opts += f' -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port or 80} -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port or 80}'
Path('/workspace/.android-tools/java-network.env').write_text('export JAVA_TOOL_OPTIONS="'+opts+'"\n')
TRUST
cat > /workspace/.android-tools/activate.sh <<'ACTIVATE'
export JAVA_HOME=/workspace/.android-tools/jdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/workspace/.android-tools/sdk
export ANDROID_USER_HOME=/workspace/.android-tools/user
export GRADLE_USER_HOME=/workspace/.android-tools/gradle-cache
export PATH="/workspace/.android-tools/gradle-8.11.1/bin:$ANDROID_HOME/platform-tools:$PATH"
source /workspace/.android-tools/java-network.env
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Duser.home=/workspace/.android-tools/home"

ACTIVATE
source /workspace/.android-tools/activate.sh
export ANDROID_USER_HOME=/workspace/.android-tools/user
export ANDROID_HOME=/workspace/.android-tools/sdk
export GRADLE_USER_HOME=/workspace/.android-tools/gradle-cache
mkdir -p /workspace/.android-tools/downloads "$ANDROID_HOME/cmdline-tools"
cd /workspace/.android-tools/downloads
if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -fsSL https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o commandlinetools.zip
  echo '5fdcc763663eefb86a5b8879697aa6088b041e70  commandlinetools.zip' | sha1sum -c -
  unzip -q commandlinetools.zip -d sdk-extract
  mv sdk-extract/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest"
  rmdir sdk-extract
fi
if [ ! -x /workspace/.android-tools/gradle-8.11.1/bin/gradle ]; then
  curl -fsSL https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip -o gradle.zip
  curl -fsSL https://services.gradle.org/distributions/gradle-8.11.1-bin.zip.sha256 -o gradle.sha256
  python3 - <<'PY'
from pathlib import Path
import hashlib
assert hashlib.sha256(Path('gradle.zip').read_bytes()).hexdigest()==Path('gradle.sha256').read_text().strip()
PY
  unzip -q gradle.zip -d /workspace/.android-tools
fi
# Accept the Android SDK licenses for this development environment.
set +o pipefail
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /workspace/.android-tools/licenses.log
license_status=${PIPESTATUS[1]}
set -o pipefail
[ "$license_status" -eq 0 ]
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'

cd /workspace/kids-trip-speedometer
./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
