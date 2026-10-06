// Render the editable layout with the unchanged supplied artwork embedded in memory.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.QURST_SHARP_MODULE || 'sharp');
const directory = __dirname;
const logo = fs.readFileSync(path.join(directory, 'qurst-transparent.png'));
const layout = fs.readFileSync(path.join(directory, 'hero-layout.svg'), 'utf8');
const svg = layout.replace('xlink:href="qurst-transparent.png"',
  `xlink:href="data:image/png;base64,${logo.toString('base64')}"`);
sharp(Buffer.from(svg)).png().toFile(path.join(directory, '..', 'hero.png'))
  .catch(error => { process.stderr.write(`${error.message}\n`); process.exitCode = 1; });
