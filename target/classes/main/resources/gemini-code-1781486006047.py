import time
from googletrans import Translator

# 初始化翻译器
translator = Translator()

def get_word_info(word):
    try:
        # 获取翻译
        result = translator.translate(word, src='en', dest='zh-cn')
        translation = result.text.replace('\n', ';') # 替换掉可能的换行符，保证一行
        
        # 谐音生成逻辑 (简易版：基于音标近似)
        # 实际开发中建议集成 gTTS 获取音标或使用专门的音标转换库
        # 这里用一个占位符示例
        homophone = f"({word}谐音处理)" 
        
        return f"{word},{translation},{homophone}"
    except Exception as e:
        return f"{word},翻译失败: {e},无"

def process_file(input_filename, output_filename):
    with open(input_filename, 'r', encoding='utf-8') as fin, \
         open(output_filename, 'w', encoding='utf-8') as fout:
        
        for line in fin:
            word = line.strip()
            if not word:
                continue
            
            info = get_word_info(word)
            fout.write(info + '\n')
            print(f"已处理: {info}")
            
            # 加上延时，防止频繁请求被API封禁
            time.sleep(0.5)

# 使用示例
process_file('words_list.txt', 'vocabulary_full.txt')