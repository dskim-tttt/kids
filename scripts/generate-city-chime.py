"""Generate our original short chime, without downloaded audio or dependencies."""
import math
from pathlib import Path
import struct
import wave

sample_rate = 22050
notes = [(0.02, 0.16, 523.25), (0.20, 0.16, 659.25), (0.38, 0.30, 783.99)]
frames = []
for i in range(int(0.75 * sample_rate)):
    t = i / sample_rate
    value = 0.0
    for start, duration, frequency in notes:
        local = t - start
        if 0 <= local < duration:
            envelope = min(local / 0.012, 1) * (1 - local / duration) ** 1.5
            value += 0.23 * envelope * math.sin(2 * math.pi * frequency * local)
    frames.append(struct.pack('<h', round(value * 32767)))
path = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/city_chime.wav'
path.parent.mkdir(parents=True, exist_ok=True)
with wave.open(str(path), 'wb') as output:
    output.setparams((1, 2, sample_rate, 0, 'NONE', 'not compressed'))
    output.writeframes(b''.join(frames))
