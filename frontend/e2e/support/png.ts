import { crc32, deflateSync } from "node:zlib";

/**
 * テスト用のPNG画像をその場で生成する。
 *
 * バイナリをリポジトリに置かないのは、画像の中身(寸法・サイズ)が
 * テストの前提そのものだからである。ファイルを差し替えられても差分では気づけないが、
 * コードで組み立てておけば前提が壊れれば型か生成結果で分かる。
 *
 * 縦横のグラデーションにしているのは、単色だと圧縮後が数百バイトになり
 * 「原寸で配信すると重い」という検証の前提が成り立たなくなるため。
 */
export function createPng(width: number, height: number): Buffer {
  // 各行の先頭にフィルタ種別のバイト(0 = フィルタ無し)を置き、以降を RGB で並べる。
  const raw = Buffer.alloc(height * (1 + width * 3));
  let offset = 0;
  for (let y = 0; y < height; y += 1) {
    raw[offset] = 0;
    offset += 1;
    for (let x = 0; x < width; x += 1) {
      raw[offset] = (x * 255) / width;
      raw[offset + 1] = (y * 255) / height;
      raw[offset + 2] = ((x + y) * 255) / (width + height);
      offset += 3;
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // ビット深度
  ihdr[9] = 2; // カラータイプ 2 = トゥルーカラー(RGB)
  // 10..12 は圧縮方式・フィルタ方式・インターレース。いずれも 0 が標準。

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/** PNGのチャンク: 長さ(4) + 種別(4) + データ + CRC32(4)。CRCは種別とデータに対して取る。 */
function chunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);

  const typeAndData = Buffer.concat([Buffer.from(type, "ascii"), data]);

  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(typeAndData), 0);

  return Buffer.concat([length, typeAndData, checksum]);
}
