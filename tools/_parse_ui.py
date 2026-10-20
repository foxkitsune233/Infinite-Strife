import re, sys, os

path = sys.argv[1] if len(sys.argv) > 1 else "tools/_ui.xml"
raw = open(path, "rb").read()
# strip a single stray leading 0xff byte sometimes emitted by `cat`
if raw[:1] == b"\xff":
    raw = raw[1:]
if raw[:2] == b"\xff\xfe":
    xml = raw.decode("utf-16-le", "replace")
elif raw[:2] == b"\xfe\xff":
    xml = raw.decode("utf-16-be", "replace")
elif raw[:3] == b"\xef\xbb\xbf":
    xml = raw.decode("utf-8-sig", "replace")
else:
    xml = raw.decode("utf-8", "replace")
out = []
for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="(\[[0-9,]+\]\[[0-9,]+\])"', xml):
    t, b = m.group(1), m.group(2)
    if t.strip():
        out.append(t + " " + b)
out_path = os.path.join(os.path.dirname(os.path.abspath(path)), "_ui_text.txt")
open(out_path, "w", encoding="utf-8").write("\n".join(out))
