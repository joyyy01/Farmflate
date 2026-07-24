export type ViewStep =
  | 'splash'
  | 'landing'
  | 'explore'
  | 'analyzing'
  | 'report_summary'
  | 'report_risks'
  | 'report_tips'
  | 'recommended_crops'
  | 'crop_suitability_report'
  | 'condition'
  | 'dashboard'
  | 'myfield'
  | 'community'
  | 'community_create'
  | 'mypage';

export type TabState = 'home' | 'myfield' | 'community' | 'settings';

export interface PostComment {
  id: string;
  author: string;
  content: string;
  timeAgo: string;
}

export interface CommunityPost {
  id: string;
  category: string;
  tagLocation?: string;
  title: string;
  content: string;
  author: string;
  timeAgo: string;
  commentCount: number;
  likeCount: number;
  isLiked?: boolean;
  isSaved?: boolean;
  imageUrl?: string;
  comments?: PostComment[];
}
