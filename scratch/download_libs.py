import os
import re
import urllib.request

# 创建文件夹
def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

# 下载文件函数，带自定义 User-Agent
def download_url(url, dest_path):
    print(f"Downloading {url} to {dest_path}...")
    req = urllib.request.Request(
        url, 
        headers={'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )
    try:
        with urllib.request.urlopen(req) as response:
            with open(dest_path, 'wb') as f:
                f.write(response.read())
    except Exception as e:
        print(f"Error downloading {url}: {e}")
        raise e

STATIC_LIB = "src/main/resources/static/lib"

def main():
    # 1. 下载基础静态库
    ensure_dir(f"{STATIC_LIB}/vue")
    ensure_dir(f"{STATIC_LIB}/element-plus")
    ensure_dir(f"{STATIC_LIB}/marked")
    ensure_dir(f"{STATIC_LIB}/simple-mind-map")

    libs = {
        "https://unpkg.com/vue@3/dist/vue.global.js": f"{STATIC_LIB}/vue/vue.global.js",
        "https://unpkg.com/element-plus/dist/index.css": f"{STATIC_LIB}/element-plus/index.css",
        "https://unpkg.com/element-plus/dist/index.full.min.js": f"{STATIC_LIB}/element-plus/index.full.min.js",
        "https://cdn.jsdelivr.net/npm/marked/marked.min.js": f"{STATIC_LIB}/marked/marked.min.js",
        "https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.css": f"{STATIC_LIB}/simple-mind-map/simpleMindMap.css",
        "https://cdn.jsdelivr.net/npm/simple-mind-map@0.14.0/dist/simpleMindMap.esm.min.js": f"{STATIC_LIB}/simple-mind-map/simpleMindMap.esm.min.js"
    }

    for url, dest in libs.items():
        download_url(url, dest)

    # 2. Google 字体下载及处理
    fonts_dir = f"{STATIC_LIB}/fonts"
    fonts_files_dir = f"{fonts_dir}/files"
    ensure_dir(fonts_files_dir)

    google_font_css_url = "https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap"
    req = urllib.request.Request(
        google_font_css_url,
        headers={'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
    )

    print(f"Fetching Google Fonts CSS...")
    with urllib.request.urlopen(req) as response:
        css_content = response.read().decode('utf-8')

    # 正则寻找所有 woff2 链接
    woff2_urls = re.findall(r'url\((https://[^\)]+\.woff2)\)', css_content)
    print(f"Found {len(woff2_urls)} font files.")

    # 下载并替换链接
    for url in woff2_urls:
        filename = url.split('/')[-1]
        local_dest = f"{fonts_files_dir}/{filename}"
        download_url(url, local_dest)
        css_content = css_content.replace(url, f"./files/{filename}")

    # 保存本地 fonts.css
    with open(f"{fonts_dir}/fonts.css", "w", encoding="utf-8") as f:
        f.write(css_content)

    print("Dependencies localization done!")

if __name__ == "__main__":
    main()
