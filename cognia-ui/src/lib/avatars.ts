export interface AvatarOption {
  file: string
  label: string
}

/**
 * Bundled avatar illustrations. Files live in public/avaters so both the Vite
 * dev server and the production build serve them from /avaters/<file>.
 */
export const AVATAR_OPTIONS: AvatarOption[] = [
  { file: '48a79271072d142d-panda-3.png', label: '熊猫' },
  { file: '94692733c1015bc5-panda-content-closed-eyes-left.png', label: '眯眼熊猫' },
  { file: '1fea4bd8535a97d8-orca-ochre.png', label: '虎鲸' },
  { file: 'a920a2fecc2389ea-ring-tailed-coati.png', label: '环尾浣熊' },
  { file: '9a40d7590a5e8b64-sheep-3.png', label: '小羊' },
  { file: '6de3117a12e17302-wide-face-owl.png', label: '圆脸猫头鹰' },
  { file: '6dad62a08c5b99fb-owl-4.png', label: '猫头鹰' },
  { file: '9777fa26a9546560-glass-frog-2.png', label: '玻璃蛙' },
  { file: '61b349663dab3bfb-horse-2.png', label: '小马' },
  { file: 'a0638479ef1b7a8c-kingfisher-2.png', label: '翠鸟' },
  { file: '990d02996c04e29f-colorpoint-cat-2.png', label: '重点色猫' },
  { file: '123deac8ef5162a0-platypus-6.png', label: '鸭嘴兽' },
  { file: 'bda4fc66c6949066-fox-2.png', label: '狐狸' },
  { file: '328f7b9a37057e72-dog-close-ears-left.png', label: '垂耳狗' },
  { file: '85230c03cb0b8c69-rabbit-bean-head-right.png', label: '豆豆兔' },
  { file: '6891c8570c66e825-miniature-schnauzer-dog-2.png', label: '雪纳瑞' },
  { file: 'f01cfda5a9e44db1-baby-zebra.png', label: '小斑马' },
  { file: 'acc9908f6f730d0c-bear-compact-muzzle-left.png', label: '小熊' },
  { file: '794515e2f94daffd-flying-squirrel.png', label: '飞鼠' },
  { file: 'b8825dec2a4b3eb6-maltese.png', label: '马尔济斯' },
  { file: 'b1d797e93e12918f-bumblebee-4.png', label: '大黄蜂' },
  { file: 'd574eb13244e7806-alpaca-7.png', label: '羊驼' },
  { file: '1bb32fb78a49993c-turkish-angora-cat-2.png', label: '安哥拉猫' },
  { file: '89c92419c6cac531-sloth-3.png', label: '树懒' },
  { file: '1d5809227ba96ade-doberman-dog-2.png', label: '杜宾' },
]

export function avatarPath(file: string): string {
  return `/avaters/${file}`
}

/** Lightweight WebP thumbnail used in the picker; the saved avatar keeps the original URL. */
export function avatarThumbPath(file: string): string {
  return `/avaters/thumbs/${file.replace(/\.png$/i, '')}.webp`
}

export function avatarThumbnailFromUrl(url: string): string {
  if (url.startsWith('/avaters/') && !url.startsWith('/avaters/thumbs/')) {
    const file = url.slice('/avaters/'.length)
    return avatarThumbPath(file)
  }
  return url
}
