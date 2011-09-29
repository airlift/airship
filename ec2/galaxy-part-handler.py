#part-handler

def list_types():
    return ["text/plain"]

def handle_part(data, ctype, filename, payload):
    if ctype not in ("__begin__", "__end__"):
        import os
        basedir = "/home/ubuntu/cloudconf"
        try:
            os.mkdir(basedir, 0775)
        except OSError:
            pass
        with open(os.path.join(basedir, filename), "w") as f:
            f.write(payload)
