# 模型下载
from modelscope import snapshot_download
model_dir = snapshot_download(
    'openai-mirror/clip-vit-base-patch16',
    cache_dir=r'D:\Data\models'
)

print(model_dir)
