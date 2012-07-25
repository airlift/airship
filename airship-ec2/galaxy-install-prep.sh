#!/bin/bash -eu

# chown all cloudconf files to ubuntu
chown -R ubuntu:ubuntu /home/ubuntu/cloudconf

# make airship dir and chown to ubuntu
mkdir -p /mnt/airship
chown ubuntu:ubuntu /mnt/airship

# run main installer as ubuntu
chmod 755 /home/ubuntu/cloudconf/airship-install.sh
su -l -c /home/ubuntu/cloudconf/airship-install.sh ubuntu
