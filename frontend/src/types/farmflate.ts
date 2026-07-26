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
  | 'field_dashboard'
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

export type CommunityAttachmentType = 'IMAGE' | 'FILE' | 'LINK';

export interface CommunityAttachment {
  id: string;
  type: CommunityAttachmentType;
  name: string;
  contentType?: string | null;
  sizeBytes?: number | null;
  url: string;
  order: number;
}

export interface CommunityPost {
  id: string;
  regionLabel: string;
  title: string;
  content: string;
  author: string;
  profileType?: string;
  timeAgo: string;
  commentCount: number;
  likeCount: number;
  isLiked?: boolean;
  isSaved?: boolean;
  attachments: CommunityAttachment[];
  comments?: PostComment[];
}
