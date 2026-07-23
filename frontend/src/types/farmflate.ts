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
  | 'crop_register'
  | 'condition'
  | 'dashboard'
  | 'myfield'
  | 'community'
  | 'community_create'
  | 'mypage';

export type TabState = 'home' | 'myfield' | 'community' | 'settings';

export interface RecommendedCrop {
  id: string;
  name?: string;
  cropName?: string;
  score?: number;
  suitabilityScore?: number;
  badgeText?: string;
  cropYieldPotential?: string;
  soilSuitability?: string;
  weatherRisk?: string;
  climateRisk?: string;
  matchReason?: string;
  iconName?: string;
  tags?: string[];
  description?: string;
}

export interface MyFieldItem {
  id: string;
  fieldName: string;
  cropName: string;
  daysPlanted: number;
  stage: string;
  statusBadge: string;
  statusBadgeColor: 'yellow' | 'blue' | 'green';
  todayTask: string;
  reportTime: string;
}

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

export interface CropOption {
  id: string;
  name: string;
  category?: string;
  iconName?: string;
  emoji?: string;
}

export interface Province {
  id: string;
  name: string;
  districts: string[];
}

export type ProvinceData = Province;

export interface AnalysisStepItem {
  id: number;
  text: string;
  status: 'pending' | 'in_progress' | 'completed';
}
