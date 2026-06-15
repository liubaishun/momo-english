def format_word_list(input_file, output_file):
    with open(input_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    formatted_lines = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        
        # 使用 split(' ', 1) 按第一个空格分割
        # 也可以根据实际情况使用 split('\t', 1) 如果是用制表符分隔
        parts = line.split(' ', 1)
        
        if len(parts) == 2:
            # 在第一个字符串后加上逗号，并重新拼接
            new_line = f"{parts[0]},{parts[1]}"
            formatted_lines.append(new_line)
        else:
            # 如果行格式不符合预期，原样保留或处理
            formatted_lines.append(line)

    # 将结果写入新文件
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(formatted_lines))

# 执行函数
format_word_list('考研词汇表.txt', 'words_formatted.txt')
print("处理完成，已生成 words_formatted.txt")