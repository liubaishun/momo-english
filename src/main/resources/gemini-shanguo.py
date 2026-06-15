import csv

input_file = "2023考研词汇闪过.csv"
output_file = "static/books/output.csv"

with open(input_file, 'r', encoding='utf-8', newline='') as fin, \
        open(output_file, 'w', encoding='utf-8', newline='') as fout:

    reader = csv.reader(fin)
    writer = csv.writer(fout)

    for row in reader:
        new_row = [
            col.replace('\r', ' ').replace('\n', ' ')
            if isinstance(col, str) else col
            for col in row
        ]
        writer.writerow(new_row)

print("处理完成")