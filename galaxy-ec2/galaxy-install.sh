#!/bin/bash -eu

properties=($(cat "/home/ubuntu/cloudconf/installer.properties"))
for n in "${properties[@]}"
do
    export "$(echo ${n/=*/})"="$(echo ${n/*=/})"
done

x=${galaxyEnvironment?install.properties does not contain a value for galaxyEnvironment}
x=${galaxyInstallBinary?install.properties does not contain a value for galaxyInstallBinary}
x=${galaxyInstallConfig?install.properties does not contain a value for galaxyInstallConfig}
x=${galaxyRepositoryUris?install.properties does not contain a value for galaxyRepositoryUris}
x=${galaxyAwsCredentialsFile?install.properties does not contain a value for galaxyAwsCredentialsFile}

repos=$(for i in $(echo $galaxyRepositoryUris | tr , ' '); do echo "--repository $i"; done)

# install galaxy
mkdir -p /home/ubuntu/bin
cp /home/ubuntu/cloudconf/galaxy /home/ubuntu/bin
chmod 755 /home/ubuntu/bin/galaxy

# todo modify bashrc as in the ruby code

# add bin to path
export PATH=$PATH:/home/ubuntu/bin

# setup filesystem environment (must be named $targetEnvironment)
galaxy environment provision-local galaxy /mnt/galaxy/ \
    --name ${galaxyEnvironment} \
    ${repos} \
    --maven-default-group-id com.proofpoint.galaxy

# use the filesystem environment as the default (should already be set, but be careful)
galaxy environment use galaxy

# add symlink to /mnt/galaxy
ln -n -f -s /mnt/galaxy /home/ubuntu/galaxy

# install server
galaxy install ${galaxyInstallBinary} ${galaxyInstallConfig}

# copy aws credentials to server
# todo ssh is broken in aws
#galaxy ssh -c @coordinator.config "mkdir -p $(dirname ${galaxyAwsCredentialsFile}) && cp /home/ubuntu/cloudconf/aws-credentials.properties ${galaxyAwsCredentialsFile}"

credentialsFile=/mnt/galaxy/coordinator/data/${galaxyAwsCredentialsFile}
mkdir -p $(dirname ${credentialsFile}) && cp /home/ubuntu/cloudconf/aws-credentials.properties ${credentialsFile}

# start server
galaxy start -c ${galaxyInstallConfig}

# add target environment
galaxy environment add ${galaxyEnvironment} http://127.0.0.1:64000/
galaxy environment use ${galaxyEnvironment}
