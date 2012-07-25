#!/bin/bash -eu

properties=($(cat "/home/ubuntu/cloudconf/installer.properties"))
for n in "${properties[@]}"
do
    export "$(echo ${n/=*/})"="$(echo ${n/*=/})"
done

x=${airshipEnvironment?install.properties does not contain a value for airshipEnvironment}
x=${airshipInstallBinary?install.properties does not contain a value for airshipInstallBinary}
x=${airshipInstallConfig?install.properties does not contain a value for airshipInstallConfig}
x=${airshipRepositoryUris?install.properties does not contain a value for airshipRepositoryUris}
if [ -e /home/ubuntu/cloudconf/aws-credentials.properties ]
then
    x=${airshipAwsCredentialsFile?install.properties does not contain a value for airshipAwsCredentialsFile}
fi

REPOS=$(for i in $(echo $airshipRepositoryUris | tr , ' '); do echo "--repository $i"; done)

# install airship
mkdir -p /home/ubuntu/bin
cp /home/ubuntu/cloudconf/airship /home/ubuntu/bin
chmod 755 /home/ubuntu/bin/airship

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
cat <<EOT > /home/ubuntu/.airship.bashrc
export PATH=\$PATH:/home/ubuntu/bin

YELLOW="\\[\\033[33m\\]"
GREEN="\\[\\033[32m\\]"
DEFAULT="\\[\\033[39m\\]"
export PS1="[\$(date +%H:%M) \u@\${YELLOW}${INSTANCE_ID}\${DEFAULT}:\w \${GREEN}$(cat ~/.airshipconfig | awk '/^environment\.default *=/ {print $3}')\${DEFAULT}] "
EOT

(grep 'airship\.bashrc' /home/ubuntu/.bashrc >> /dev/null) || echo -e "\n. ~/.airship.bashrc\n" >> /home/ubuntu/.bashrc

# setup filesystem environment (must be named $targetEnvironment)
airship environment provision-local airship /mnt/airship/ \
    --name ${airshipEnvironment} \
    ${REPOS} \
    --maven-default-group-id io.airlift.airship \
    --agent-id $INSTANCE_ID \
    --location $LOCATION \
    --instance-type $INSTANCE_TYPE \
    --external-address $EXTERNAL_ADDRESS \
    --internal-ip $INTERNAL_IP

# use the filesystem environment as the default (should already be set, but be careful)
airship environment use airship

# local environment should show internal addresses
airship config set environment.airship.use-internal-address true

# add symlink to /mnt/airship
ln -n -f -s /mnt/airship /home/ubuntu/airship

# install server
airship --batch install ${airshipInstallBinary} ${airshipInstallConfig}

if [ -e /home/ubuntu/cloudconf/aws-credentials.properties ]
then
    # copy aws credentials to server
    airship ssh --all "mkdir -p $(dirname ${airshipAwsCredentialsFile}) && cp /home/ubuntu/cloudconf/aws-credentials.properties ${airshipAwsCredentialsFile}"
fi

# start server
airship --batch start --all

# add target environment
airship environment add ${airshipEnvironment} ${airshipCoordinatorUri:-http://127.0.0.1:64000/}
airship environment use ${airshipEnvironment}

# target environment should show internal addresses
airship config set environment.${airshipEnvironment}.use-internal-address true
