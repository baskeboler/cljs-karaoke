#!/usr/bin/env fish

for midi in *.mid
    timidity $midi -Ow -o - | ffmpeg -i - -acodec libmp3lame -ab 64k "$midi.mp3"
end
