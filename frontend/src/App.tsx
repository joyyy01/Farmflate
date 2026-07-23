import { useState } from 'react';
import type { ViewStep, TabState, CommunityPost, MyFieldItem } from './types/farmflate';
import { SplashView } from './components/farmflate/SplashView';
import { LandingView } from './components/farmflate/LandingView';
import { RegionExploreView } from './components/farmflate/RegionExploreView';
import { AnalyzingView } from './components/farmflate/AnalyzingView';
import { RegionReportSummaryView } from './components/farmflate/RegionReportSummaryView';
import { RegionRisksView } from './components/farmflate/RegionRisksView';
import { RegionTipsView } from './components/farmflate/RegionTipsView';
import { RecommendedCropsView } from './components/farmflate/RecommendedCropsView';
import { CropSuitabilityReportView } from './components/farmflate/CropSuitabilityReportView';
import { CropConditionInputView } from './components/farmflate/CropConditionInputView';
import { MainDashboardView } from './components/farmflate/MainDashboardView';
import { MyFieldListView } from './components/farmflate/MyFieldListView';
import { CommunityListView } from './components/farmflate/CommunityListView';
import { CommunityCreatePostView } from './components/farmflate/CommunityCreatePostView';
import { MyPageView } from './components/farmflate/MyPageView';
import { AIChatModal } from './components/farmflate/AIChatModal';
import { MOCK_MY_FIELDS, MOCK_COMMUNITY_POSTS } from './data/mockData';
import { analyzeCropSuitability, type DynamicAnalysisResult } from './services/farmEngine';

type ExtendedViewStep = ViewStep | 'splash';

export function App() {
  const [viewStep, setViewStep] = useState<ExtendedViewStep>('splash');
  const [activeTab, setActiveTab] = useState<TabState>('home');
  const [isAIChatOpen, setIsAIChatOpen] = useState(false);
  const [isNewUser, setIsNewUser] = useState(true);

  /* User State & Selections */
  const [userName, _setUserName] = useState('진우님');
  const [selectedProvince, setSelectedProvince] = useState('전북특별자치도');
  const [selectedDistrict, setSelectedDistrict] = useState('고창군');
  const [selectedCropName, setSelectedCropName] = useState('감자');
  const [analysisResult, setAnalysisResult] = useState<DynamicAnalysisResult>(
    analyzeCropSuitability('전북특별자치도', '고창군', '감자')
  );

  /* Dynamic Fields & Posts */
  const [myFields, setMyFields] = useState<MyFieldItem[]>(MOCK_MY_FIELDS);
  const [posts, setPosts] = useState<CommunityPost[]>(MOCK_COMMUNITY_POSTS);

  /* Region Analysis Handler */
  const handleStartAnalysis = (province: string, district: string) => {
    setSelectedProvince(province);
    setSelectedDistrict(district);
    const result = analyzeCropSuitability(province, district, selectedCropName);
    setAnalysisResult(result);
    setViewStep('analyzing');
  };

  /* Analysis Completion Handler */
  const handleAnalysisComplete = () => {
    setViewStep('report_summary');
  };

  /* Crop Input Handler */
  const handleStartCropConditionAnalysis = (cropName: string, _stage: string, _mode: string) => {
    setSelectedCropName(cropName);
    const result = analyzeCropSuitability(selectedProvince, selectedDistrict, cropName);
    setAnalysisResult(result);
    setViewStep('analyzing');
  };

  /* Dynamic Add Field Handler */
  const handleAddField = () => {
    const newField: MyFieldItem = {
      id: Date.now().toString(),
      fieldName: `${selectedCropName}밭`,
      cropName: selectedCropName,
      daysPlanted: 1,
      stage: '생장 초기',
      statusBadge: '물주기 필요',
      statusBadgeColor: 'yellow',
      todayTask: `${selectedCropName}밭 토양 수분 체크 및 물주기`,
      reportTime: '방금 전 자동 분석됨'
    };
    
    // 신규 유저 상태 해제 및 데이터 삽입
    setIsNewUser(false);
    
    // 만약 기존에 MOCK_MY_FIELDS가 채워져 있었다면 초기화 후 새로 넣도록 설정
    setMyFields(prev => isNewUser ? [newField] : [newField, ...prev]);
    setViewStep('myfield');
    setActiveTab('myfield');
  };

  /* Tab Navigation Handler */
  const handleTabChange = (tab: TabState) => {
    setActiveTab(tab);
    if (tab === 'home') setViewStep('dashboard');
    if (tab === 'myfield') setViewStep('myfield');
    if (tab === 'community') setViewStep('community');
    if (tab === 'settings') setViewStep('mypage');
  };

  /* Community Create Handler */
  const handleCreatePost = (title: string, content: string) => {
    const newPost: CommunityPost = {
      id: Date.now().toString(),
      category: '재배 꿀팁',
      tagLocation: `${selectedProvince} ${selectedDistrict}`,
      title,
      content,
      author: userName,
      timeAgo: '방금 전',
      commentCount: 0,
      likeCount: 0,
      isSaved: false
    };
    setPosts(prev => [newPost, ...prev]);
    setViewStep('community');
    setActiveTab('community');
  };

  return (
    <div className="mobile-wrapper">
      {/* 0. Splash Screen */}
      {viewStep === 'splash' && (
        <SplashView onComplete={() => setViewStep('landing')} />
      )}

      {/* 1. Landing Screen */}
      {viewStep === 'landing' && (
        <LandingView onLogin={() => setViewStep('dashboard')} />
      )}

      {/* 2. Region Search Screen */}
      {viewStep === 'explore' && (
        <RegionExploreView
          onBack={() => setViewStep('dashboard')}
          onStartAnalysis={handleStartAnalysis}
        />
      )}

      {/* 3. Crop Condition Input Screen */}
      {viewStep === 'condition' && (
        <CropConditionInputView
          onBack={() => setViewStep('dashboard')}
          onStartAnalysis={handleStartCropConditionAnalysis}
          onOpenExplore={() => setViewStep('explore')}
          selectedRegionName={`${selectedProvince} ${selectedDistrict}`}
        />
      )}

      {/* 4. Animated Analysis Loading Screen */}
      {viewStep === 'analyzing' && (
        <AnalyzingView
          regionName={`${selectedProvince} ${selectedDistrict}`}
          onComplete={handleAnalysisComplete}
        />
      )}

      {/* 5. Region Report Summary Screen */}
      {viewStep === 'report_summary' && (
        <RegionReportSummaryView
          regionName={`${selectedProvince} ${selectedDistrict}`}
          onNext={() => setViewStep('report_risks')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 6. Region Risks Screen */}
      {viewStep === 'report_risks' && (
        <RegionRisksView
          onBack={() => setViewStep('report_summary')}
          onNext={() => setViewStep('report_tips')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 7. Region Tips Screen */}
      {viewStep === 'report_tips' && (
        <RegionTipsView
          districtName={selectedDistrict}
          onGoToCreateField={() => setViewStep('crop_suitability_report')}
          onGoToRecommendedCrops={() => setViewStep('recommended_crops')}
        />
      )}

      {/* 8. Recommended Crops Screen */}
      {viewStep === 'recommended_crops' && (
        <RecommendedCropsView
          districtName={selectedDistrict}
          onBack={() => setViewStep('report_tips')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          onSelectCrop={(cropName) => {
            setSelectedCropName(cropName);
            const result = analyzeCropSuitability(selectedProvince, selectedDistrict, cropName);
            setAnalysisResult(result);
            setViewStep('crop_suitability_report');
          }}
        />
      )}

      {/* 9. Crop Suitability Report Screen */}
      {viewStep === 'crop_suitability_report' && (
        <CropSuitabilityReportView
          cropName={selectedCropName}
          score={analysisResult.score}
          onBack={() => setViewStep('recommended_crops')}
          onRegisterCrop={handleAddField}
        />
      )}

      {/* 10. Main Dashboard Screen */}
      {viewStep === 'dashboard' && (
        <MainDashboardView
          userName={userName}
          analyzedRegion={`${selectedProvince} ${selectedDistrict}`}
          analyzedCropResult={null}
          onGoToExplore={() => setViewStep('explore')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          isNewUser={isNewUser}
        />
      )}

      {/* 11. My Field List Screen */}
      {viewStep === 'myfield' && (
        <MyFieldListView
          fields={isNewUser ? [] : myFields}
          onAddField={() => setViewStep('explore')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
        />
      )}

      {/* 12. Community List Screen */}
      {viewStep === 'community' && (
        <CommunityListView
          posts={posts}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          onOpenWrite={() => setViewStep('community_create')}
          activeTab={activeTab}
          onTabChange={handleTabChange}
        />
      )}

      {/* 13. Community Create Screen */}
      {viewStep === 'community_create' && (
        <CommunityCreatePostView
          onCancel={() => setViewStep('community')}
          onSubmitPost={handleCreatePost}
        />
      )}

      {/* 14. My Page (Settings) Screen */}
      {viewStep === 'mypage' && (
        <MyPageView
          userName={userName}
          userRegion={`${selectedProvince} ${selectedDistrict}`}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
        />
      )}

      {/* 15. Global Farmflate AI Bottom Sheet Modal */}
      <AIChatModal
        isOpen={isAIChatOpen}
        onClose={() => setIsAIChatOpen(false)}
        selectedRegion={`${selectedProvince} ${selectedDistrict}`}
        selectedCropInfo={selectedCropName}
      />
    </div>
  );
}

export default App;
