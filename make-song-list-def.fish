#!/usr/bin/env fish

echo "(def song-list"
echo "  ["
for f in public/lyrics/*.edn
    set filename (echo $f | replace ".edn" "" "public/lyrics/" "")
    echo "    \"$filename\""
end
echo "])"
