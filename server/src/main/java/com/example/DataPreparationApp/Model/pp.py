import os

output_file = "combined_output.txt"
current_script = os.path.basename(__file__)

with open(output_file, 'w', encoding='utf-8') as outfile:
    for filename in os.listdir('.'):
        if filename == current_script or filename == output_file:
            continue
        if os.path.isfile(filename):
            try:
                with open(filename, 'r', encoding='utf-8') as infile:
                    outfile.write(f"--- Contents of {filename} ---\n")
                    outfile.write(infile.read())
                    outfile.write("\n\n")
            except Exception as e:
                print(f"Could not read {filename}: {e}")

print(f"All files combined into '{output_file}'.")
