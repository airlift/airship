#!/bin/bash -eu

properties=($(cat "/home/ubuntu/cloudconf/installer.properties"))
for n in "${properties[@]}"
do
    export "$(echo ${n/=*/})"="$(echo ${n/*=/})"
done

x=${galaxyEnvironment?install.properties does not contain a value for galaxyEnvironment}
x=${galaxyVersion?install.properties does not contain a value for galaxyVersion}

# install galaxy
mkdir -p /home/ubuntu/bin
cp /home/ubuntu/cloudconf/galaxy /home/ubuntu/bin
chmod 755 /home/ubuntu/bin/galaxy

# todo modify bashrc as in the ruby code

# add bin to path
export PATH=$PATH:/home/ubuntu/bin

# create local repository
mkdir -p /home/ubuntu/local-repository
cp /home/ubuntu/cloudconf/galaxy-coordinator.config /home/ubuntu/local-repository

# setup filesystem environment (must be named $targetEnvironment)
galaxy environment provision-local galaxy /mnt/galaxy/ \
    --name ${galaxyEnvironment} \
    --repository https://oss.sonatype.org/content/repositories/releases/ \
    --repository https://oss.sonatype.org/content/repositories/snapshots/ \
    --repository file:///home/ubuntu/local-repository/ \
    --maven-default-group-id com.proofpoint.galaxy

# use the filesystem environment as the default (should already be set, but be careful)
galaxy environment use galaxy

# todo add symlink to /mnt/galaxy

# install galaxy coordinator
galaxy install galaxy-coordinator:${galaxyVersion} @galaxy-coordinator.config

# start coordinator
galaxy start -c '@galaxy-coordinator.config'

# add target evironment
galaxy environment add ${galaxyEnvironment} http://127.0.0.1:64000/
galaxy environment use ${galaxyEnvironment}
