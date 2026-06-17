import sys

def check_braces(filepath):
    with open(filepath, 'r') as f:
        lines = f.readlines()
    
    depth = 0
    in_story_comment = False
    in_block_comment = False
    
    for idx, line in enumerate(lines, 1):
        clean_line = ""
        # Handle string literals and comments to prevent false brace counts
        i = 0
        while i < len(line):
            if in_block_comment:
                if line[i:i+2] == "*/":
                    in_block_comment = False
                    i += 2
                else:
                    i += 1
                continue
            if line[i:i+2] == "/*":
                in_block_comment = True
                i += 2
                continue
            if line[i:i+2] == "//":
                break
            
            # Match characters
            clean_line += line[i]
            i += 1
            
        for char in clean_line:
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
        
        # We are interested in GitHubTrackerScreen which is lines 2293 onwards
        if idx >= 2290 and idx <= 3540:
            print(f"Line {idx:04d}: depth={depth} | {line.strip()}")

if __name__ == "__main__":
    check_braces("/app/src/main/java/com/example/ui/MainLayout.kt")
