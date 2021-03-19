#!/bin/sh

#
# Copyright 2021 Monun
#
# Licensed under the Apache License, Version 3.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://opensource.org/licenses/gpl-3.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

download() {
  download_result=$(wget -c --content-disposition -P "$2" -N "$1" 2>&1 | tail -2 | head -1)
  echo "$download_result"
}

build=true
debug=true
backup=false
restart=false

version=1.16.5
memory=4
debug_port=5005
jar_url="https://papermc.io/api/v1/paper/$version/latest/download"
download_plugins=(
  'https://github.com/monun/kotlin-plugin/releases/latest/download/Kotlin-1.4.31.jar'
  'https://github.com/monun/invfx/releases/latest/download/InvFX.jar'
  'https://github.com/monun/auto-update/releases/latest/download/AutoUpdate.jar'
  'https://ci.dmulloy2.net/job/ProtocolLib/lastSuccessfulBuild/artifact/target/ProtocolLib.jar'
)

# Print configurations
echo "build = $build"
echo "debug = $debug"
echo "backup = $backup"
echo "restart = $restart"
echo "version = $version"
echo "memory = ${memory}G"

project_folder=$(pwd)
script=$(basename "$0")
server_folder="$project_folder/.${script%.*}"
mkdir -p "$server_folder"
mkdir -p "$server_folder/plugins"
mkdir -p "$HOME/.minecraft/servers"

cd "$server_folder" || exit

# Download jar
jar_result=$(download "$jar_url" "$HOME/.minecraft/servers")
jar=$(grep -oG "‘.*’" <<< $jar_result)
jar="${jar:1:-1}"
echo "$jar_result"

# Download plugins
for i in "${download_plugins[@]}"
do
  download "$i" "./plugins"
done

jvm_arguments=(
  "-Xmx${memory}G"
  "-Xms${memory}G"
  "-XX:+ParallelRefProcEnabled"
  "-XX:MaxGCPauseMillis=200"
  "-XX:+UnlockExperimentalVMOptions"
  "-XX:+DisableExplicitGC"
  "-XX:+AlwaysPreTouch"
  "-XX:G1HeapWastePercent=5"
  "-XX:G1MixedGCCountTarget=4"
  "-XX:G1MixedGCLiveThresholdPercent=90"
  "-XX:G1RSetUpdatingPauseTimePercent=5"
  "-XX:SurvivorRatio=32"
  "-XX:+PerfDisableSharedMem"
  "-XX:MaxTenuringThreshold=1"
  "-Dusing.aikars.flags=https://mcflags.emc.gs"
  "-Daikars.new.flags=true"
  "-Dcom.mojang.eula.agree=true"
)

if (($memory < 12))
then
  jvm_arguments+=(
    "-XX:G1NewSizePercent=30"
    "-XX:G1MaxNewSizePercent=40"
    "-XX:G1HeapRegionSize=8M"
    "-XX:G1ReservePercent=20"
    "-XX:InitiatingHeapOccupancyPercent=15"
 )
else
  echo "Enable high-capacity memory jvm options"
  jvm_arguments+=(
    "-XX:G1NewSizePercent=40"
    "-XX:G1MaxNewSizePercent=50"
    "-XX:G1HeapRegionSize=16M"
    "-XX:G1ReservePercent=15"
    "-XX:InitiatingHeapOccupancyPercent=20"
  )
fi

if ($debug)
then
  jvm_arguments+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debug_port")
fi

jvm_arguments+=(
  "-jar"
  "$jar"
  "--nogui"
)

while :
do
  if ($build)
  then
    cd "$project_folder" || exit
    ./gradlew clean copyToServer
    cd "$server_folder" || exit
  fi

  java "${jvm_arguments[@]}"

  if ($backup)
  then
    read -r -t 5 -p "Press Enter to start the backup immediately `echo $'\n> '`"
    echo 'Start the backup.'
    backup_file_name=$(date +"%y%m%d-%H%M%S")
    mkdir -p '.backup'
    tar --exclude='./.backup' --exclude='*.gz' --exclude='./cache' -zcf ".backup/$backup_file_name.tar.gz" .
    echo 'The backup is complete.'
  fi

  if (! ($restart))
  then
    break
  fi

  read -r -t 5 -p "The server restarts. Press Enter to start immediately or Ctrl+C to cancel `echo $'\n> '`"
done