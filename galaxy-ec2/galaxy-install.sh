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

# add bin to path
export PATH=$PATH:/home/ubuntu/bin

# build location string
ZONE=$(curl -s "http://169.254.169.254/latest/meta-data/placement/availability-zone")
INSTANCE_ID=$(curl -s "http://169.254.169.254/latest/meta-data/instance-id")
REGION=${ZONE[@]:0:$(( ${#ZONE} - 1 )) }
LOCATION="/ec2/${REGION}/${ZONE}/${INSTANCE_ID}"

# instance type
INSTANCE_TYPE=$(curl -s "http://169.254.169.254/latest/meta-data/instance-type")

# external address
EXTERNAL_ADDRESS=$(curl -s "http://169.254.169.254/latest/meta-data/public-hostname")

# internal ip
INTERNAL_IP=$(curl -s "http://169.254.169.254/latest/meta-data/local-ipv4")

# add to bashrc
cat <<EOT > /home/ubuntu/.galaxy.bashrc
export PATH=\$PATH:/home/ubuntu/bin

YELLOW="\\[\\033[33m\\]"
GREEN="\\[\\033[32m\\]"
DEFAULT="\\[\\033[39m\\]"
export PS1="[\$(date +%H:%M) \u@\${YELLOW}${INSTANCE_ID}\${DEFAULT}:\w \${GREEN}$(cat ~/.galaxyconfig | awk '/^environment\.default *=/ {print $3}')\${DEFAULT}] "
EOT

(grep 'galaxy\.bashrc' /home/ubuntu/.bashrc >> /dev/null) || echo -e "\n. ~/.galaxy.bashrc\n" >> /home/ubuntu/.bashrc

# setup filesystem environment (must be named $targetEnvironment)
galaxy environment provision-local galaxy /mnt/galaxy/ \
    --name ${galaxyEnvironment} \
    ${REPOS} \
    --maven-default-group-id com.proofpoint.galaxy \
    --agent-id $INSTANCE_ID \
    --location $LOCATION \
    --instance-type $INSTANCE_TYPE \
    --external-address $EXTERNAL_ADDRESS \
    --internal-ip $INTERNAL_IP

# use the filesystem environment as the default (should already be set, but be careful)
galaxy environment use galaxy

# local environment should show internal addresses
galaxy config set environment.galaxy.use-internal-address true

# add symlink to /mnt/galaxy
ln -n -f -s /mnt/galaxy /home/ubuntu/galaxy

# install server
galaxy --batch install ${galaxyInstallBinary} ${galaxyInstallConfig}

if [ -e /home/ubuntu/cloudconf/aws-credentials.properties ]
then
    # copy aws credentials to server
    galaxy ssh --all "mkdir -p $(dirname ${galaxyAwsCredentialsFile}) && cp /home/ubuntu/cloudconf/aws-credentials.properties ${galaxyAwsCredentialsFile}"
fi

# start server
galaxy --batch start --all

# add target environment
galaxy environment add ${galaxyEnvironment} ${galaxyCoordinatorUri:-http://127.0.0.1:64000/}
galaxy environment use ${galaxyEnvironment}

# target environment should show internal addresses
galaxy config set environment.${galaxyEnvironment}.use-internal-address true
