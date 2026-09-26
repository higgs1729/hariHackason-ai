import type { ShootHint } from './api'

export interface ShootIdea {
  /** what the app shouts at the group */
  title: string
  /** how to shoot it, one short step each */
  steps: string[]
  /** fewest people it works with */
  minMembers: number
}

/**
 * Prikura prompts from the design lead (okinn), 2026-09-26. Shown as 「お題」 in
 * the shoot hint sheet and used by the mock hint. They are also the few-shot
 * examples asked of the backend prompt (docs/design/08-shoot-ideas.md).
 */
export const shootIdeas: ShootIdea[] = [
  { title: 'みんなで一斉にジャンプ！', steps: ['3、2、1でジャンプ', 'スロー再生すると青春っぽい'], minMembers: 1 },
  { title: '今度は一人ずつ増えてみよう！', steps: ['最初は一人だけ', '1枚撮るごとに一人ずつ増える', '最後は全員集合'], minMembers: 2 },
  { title: '空中で止まろう！', steps: ['ジャンプした瞬間を連写', 'つなげると空中に浮いて見える'], minMembers: 1 },
  { title: 'みんなで瞬間移動！', steps: ['同じポーズのまま', '場所を少しずつずらして撮る', 'つなげるとスーッと移動して見える'], minMembers: 1 },
  { title: '全員で変なことして！', steps: ['全員で変な動き', '数年後に見返すと絶対笑う'], minMembers: 2 },
  { title: 'カメラを順番にのぞき込んで！', steps: ['一人ずつ画面に顔を出す', '最後に全員集合'], minMembers: 2 },
  { title: '秘密の合言葉を決めよう！', steps: ['その場で謎の言葉を決める', '全員で一緒に叫ぶ', '未来で見て「何これ？」ってなる'], minMembers: 2 },
  { title: 'この続きを未来で撮ろう！', steps: ['一人が手を差し出すところまで撮る', '再会したら相手がその手を取る', '2本つないで完成'], minMembers: 2 },
  { title: 'みんなで文字を作ってみよう！', steps: ['1枚ごとに少しずつ移動', '最後にハートなどの形を作る'], minMembers: 3 },
  { title: '未来で完成させる動画を撮ろう！', steps: ['全員で「せーの……」まで撮って終了', '再会した日に続きを撮る'], minMembers: 2 },
  { title: '違う場所で撮ってみよう！', steps: ['いろんな場所で', '同じポーズ・同じ構図で撮る'], minMembers: 1 },
  { title: 'みんなで歩こう！', steps: ['少しずつ前に進みながら撮る', '最後に大きな一歩！'], minMembers: 1 },
]

export const ideaToHint = (idea: ShootIdea): ShootHint => ({ hint: idea.title, poses: idea.steps, aiGenerated: 0 })
