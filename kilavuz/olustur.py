#!/usr/bin/env python3
"""kilavuz.html -> Bideng-Kullanim-Kilavuzu.pdf ve .docx (resimler gömülü)."""
import base64, re, subprocess, tempfile
from pathlib import Path

here = Path(__file__).resolve().parent
html = (here / "kilavuz.html").read_text(encoding="utf-8")

def embed(match):
    data = base64.b64encode((here / match.group(1)).read_bytes()).decode()
    return f'src="data:image/jpeg;base64,{data}"'

embedded = re.sub(r'src="(img/[^"]+)"', embed, html)
with tempfile.TemporaryDirectory() as temp:
    source = Path(temp) / "Bideng-Kullanim-Kilavuzu.html"
    source.write_text(embedded, encoding="utf-8")
    subprocess.run(["soffice", "--headless", "--convert-to", "odt:writerweb8_writer", "--outdir", temp, str(source)], check=True, capture_output=True)
    odt = Path(temp) / "Bideng-Kullanim-Kilavuzu.odt"
    for target in ("pdf:writer_pdf_Export", "docx:MS Word 2007 XML"):
        subprocess.run(["soffice", "--headless", "--convert-to", target, "--outdir", str(here), str(odt)], check=True, capture_output=True)
print("tamam")
