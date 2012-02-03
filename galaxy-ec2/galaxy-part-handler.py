#part-handler

import base64
import os
import urllib

def list_types():
    return ["text/plain", "application/octet-stream", "text/x-url"]

def handle_part(data, ctype, filename, payload):
    if ctype not in ("__begin__", "__end__"):
        basedir = "/home/ubuntu/cloudconf"
        try:
            os.mkdir(basedir, 0775)
        except OSError:
            pass
        target = os.path.join(basedir, filename)
        if ctype == "text/x-url":
            urllib.URLopener().retrieve(payload, target)
        else:
            with open(target, "w") as f:
                if ctype == "application/octet-stream":
                    f.write(base64.b64decode(base64.b64decode(payload)))
                else:
                    f.write(payload)
