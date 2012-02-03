#!/bin/bash -eu

# chown all cloudconf files to ubuntu
chown -R ubuntu:ubuntu /home/ubuntu/cloudconf

# make galaxy dir and chown to ubuntu
mkdir -p /mnt/galaxy
chown ubuntu:ubuntu /mnt/galaxy

# run main installer as ubuntu
chmod 755 /home/ubuntu/cloudconf/galaxy-install.sh
su -l -c /home/ubuntu/cloudconf/galaxy-install.sh ubuntu
