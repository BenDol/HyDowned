#!/bin/bash
# Convert println statements to Log calls

find src/main/kotlin/com/hydowned -name "*.kt" -type f | while read file; do
  # Add Log import if println exists and Log import doesn't
  if grep -q "println" "$file" && ! grep -q "import com.hydowned.util.Log" "$file"; then
    # Find the last import line and add Log import after it
    sed -i '/^import/{ :loop; n; /^$/! b loop; i\import com.hydowned.util.Log\n
    }' "$file"
  fi
  
  # Convert common separator patterns
  sed -i 's/println("\[HyDowned\] ============================================")/Log.separator(category)/g' "$file"
  
  # Convert error messages (with ⚠ or ✗)
  sed -i 's/println("\[HyDowned\] \[.*\] ⚠ \(.*\)")/Log.warning(category, "\1")/g' "$file"
  sed -i 's/println("\[HyDowned\] ⚠ \(.*\)")/Log.warning(category, "\1")/g' "$file"
  sed -i 's/println("\[HyDowned\] \[.*\] ✗ \(.*\)")/Log.error(category, "\1")/g' "$file"
  sed -i 's/println("\[HyDowned\] ✗ \(.*\)")/Log.error(category, "\1")/g' "$file"
  
  # Convert success messages (with ✓)
  sed -i 's/println("\[HyDowned\] \[.*\] ✓ \(.*\)")/Log.verbose(category, "\1")/g' "$file"
  sed -i 's/println("\[HyDowned\] ✓ \(.*\)")/Log.verbose(category, "\1")/g' "$file"
done
