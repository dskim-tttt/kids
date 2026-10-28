"""Generate three original local samples without downloaded audio or dependencies."""
import math
from pathlib import Path
import struct
import wave

SAMPLE_RATE = 22050
SAMPLES = {
    'city_chime': (0.75, [(0.02, 0.16, 523.25), (0.20, 0.16, 659.25), (0.38, 0.30, 783.99)], 'sine'),
    'city_bell': (0.95, [(0.02, 0.85, 659.25)], 'bell'),
    'city_sparkle': (0.85, [(0.02, 0.20, 659.25), (0.16, 0.20, 783.99), (0.30, 0.20, 987.77), (0.44, 0.30, 1046.50)], 'sine'),
}

folder = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
folder.mkdir(parents=True, exist_ok=True)
for name, (duration, notes, timbre) in SAMPLES.items():
    frames = []
    for i in range(int(duration * SAMPLE_RATE)):
        t = i / SAMPLE_RATE
        value = 0.0
        for start, length, frequency in notes:
            local = t - start
            if 0 <= local < length:
                envelope = min(local / 0.012, 1) * (1 - local / length) ** 1.5
                tone = math.sin(2 * math.pi * frequency * local)
                if timbre == 'bell':
                    tone = 0.8 * tone + 0.15 * math.sin(2 * math.pi * frequency * 2 * local) + 0.05 * math.sin(2 * math.pi * frequency * 3 * local)
                value += 0.23 * envelope * tone
        frames.append(struct.pack('<h', round(max(-1, min(1, value)) * 32767)))
    with wave.open(str(folder / (name + '.wav')), 'wb') as output:
        output.setparams((1, 2, SAMPLE_RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(b''.join(frames))
