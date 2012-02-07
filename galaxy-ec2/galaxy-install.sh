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
if [ -e /home/ubuntu/cloudconf/aws-credentials.properties ]
then
    x=${galaxyAwsCredentialsFile?install.properties does not contain a value for galaxyAwsCredentialsFile}
fi

REPOS=$(for i in $(echo $galaxyRepositoryUris | tr , ' '); do echo "--repository $i"; done)

# install galaxy
mkdir -p /home/ubuntu/bin
cp /home/ubuntu/cloudconf/galaxy /home/ubuntu/bin
chmod 755 /home/ubuntu/bin/galaxy

# todo modify bashrc as in the ruby code

# add bin to path
export PATH=$PATH:/home/ubuntu/bin

# build location string
ZONE=$(curl -s "http://169.254.169.254/latest/meta-data/placement/availability-zone")
INSTANCE_ID=$(curl -s "http://169.254.169.254/latest/meta-data/instance-id")
REGION=${ZONE[@]:0:$(( ${#ZONE} - 1 )) }
LOCATION="/ec2/${REGION}/${ZONE}/${INSTANCE_ID}"

# instance type
INSTANCE_TYPE=$(curl -s "http://169.254.169.254/latest/meta-data/instance-type")

# setup filesystem environment (must be named $targetEnvironment)
galaxy environment provision-local galaxy /mnt/galaxy/ \
    --name ${galaxyEnvironment} \
    ${REPOS} \
    --maven-default-group-id com.proofpoint.galaxy \
    --agent-id $INSTANCE_ID \
    --location $LOCATION \
    --instance-type $INSTANCE_TYPE

# use the filesystem environment as the default (should already be set, but be careful)
galaxy environment use galaxy

# add symlink to /mnt/galaxy
ln -n -f -s /mnt/galaxy /home/ubuntu/galaxy

# install server
galaxy install ${galaxyInstallBinary} ${galaxyInstallConfig}

if [ -e /home/ubuntu/cloudconf/aws-credentials.properties ]
then
    # copy aws credentials to server
    galaxy ssh -c @coordinator.config "mkdir -p $(dirname ${galaxyAwsCredentialsFile}) && cp /home/ubuntu/cloudconf/aws-credentials.properties ${galaxyAwsCredentialsFile}"
fi

# start server
galaxy start -c ${galaxyInstallConfig}

# add target environment
galaxy environment add ${galaxyEnvironment} http://127.0.0.1:64000/
galaxy environment use ${galaxyEnvironment}
