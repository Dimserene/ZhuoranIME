from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
out=root/'vendor/assets'
shutil.rmtree(out,ignore_errors=True)
shutil.copytree(root/'vendor/dictionary',out/'chewing')
shutil.copytree(root/'third_party',out/'third_party')
