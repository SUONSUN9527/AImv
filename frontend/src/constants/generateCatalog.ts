import type { ChainType } from '../types/api';

export type CreationCard = {
  title: string;
  subtitle: string;
  badge: string;
  tone: string;
  chainType: ChainType;
};

export type DiscoveryItem = {
  title: string;
  meta: string;
  image: string;
  size: string;
};

// 生成首页只消费展示数据，静态素材集中在这里，避免页面组件继续堆配置。
export const creationCards: CreationCard[] = [
  {
    title: '图片生成',
    subtitle: '智能美学提升',
    badge: '4.1',
    tone: 'cyan',
    chainType: 'IMAGE'
  },
  {
    title: '视频生成',
    subtitle: '一镜到底',
    badge: 'S2',
    tone: 'blue',
    chainType: 'VIDEO'
  }
];

export const discoveryItems: DiscoveryItem[] = [
  {
    title: '健力宝来电 AI 整活大赛',
    meta: '已有 308 人参与',
    image: 'https://images.unsplash.com/photo-1519074069444-1ba4fff66d16?auto=format&fit=crop&w=1100&q=90',
    size: 'wide'
  },
  {
    title: '机器人牛仔角色设定',
    meta: '角色一致性参考',
    image: 'https://images.unsplash.com/photo-1485827404703-89b55fcc595e?auto=format&fit=crop&w=700&q=80',
    size: 'tall'
  },
  {
    title: '奇幻生物设计稿',
    meta: '图像生成',
    image: 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=700&q=80',
    size: 'tall'
  },
  {
    title: '人物肖像短片封面',
    meta: '视频封面',
    image: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=700&q=80',
    size: 'tall'
  },
  {
    title: '橘猫主角剧照',
    meta: '图片资产',
    image: 'https://images.unsplash.com/photo-1518791841217-8f162f1e1131?auto=format&fit=crop&w=700&q=80',
    size: ''
  },
  {
    title: '霓虹城市追逐',
    meta: '短片氛围',
    image: 'https://images.unsplash.com/photo-1519608487953-e999c86e7455?auto=format&fit=crop&w=700&q=90',
    size: ''
  },
  {
    title: '海边角色概念图',
    meta: '图片资产',
    image: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=700&q=90',
    size: ''
  },
  {
    title: '未来工作室镜头',
    meta: '场景参考',
    image: 'https://images.unsplash.com/photo-1518005020951-eccb494ad742?auto=format&fit=crop&w=700&q=90',
    size: ''
  },
  {
    title: '雨夜街区分镜',
    meta: '镜头灵感',
    image: 'https://images.unsplash.com/photo-1514565131-fce0801e5785?auto=format&fit=crop&w=700&q=90',
    size: ''
  },
  {
    title: '电影感人物光影',
    meta: '封面参考',
    image: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=700&q=90',
    size: ''
  },
  {
    title: '室内产品运镜',
    meta: '视频参考',
    image: 'https://images.unsplash.com/photo-1494438639946-1ebd1d20bf85?auto=format&fit=crop&w=700&q=90',
    size: ''
  }
];
