import { useState, useEffect } from 'react';
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
import { MOCK_MY_FIELDS, MOCK_COMMUNITY_POSTS } from './data/mockData';
import { analyzeCropSuitability, getSidoCode, getSigunguCode, type DynamicAnalysisResult } from './services/farmEngine';
import { ApiService } from './services/api';
import type { RegionReport, HomeData } from './services/api';
import { AIChatModal } from './components/farmflate/AIChatModal';

type ExtendedViewStep = ViewStep | 'splash';

export function App() {
  const checkHasToken = () => !!(localStorage.getItem('jwtToken') || localStorage.getItem('token'));
  const [viewStep, setViewStep] = useState<ExtendedViewStep>(checkHasToken() ? 'dashboard' : 'landing');
  const [activeTab, setActiveTab] = useState<TabState>('home');
  const [isAIChatOpen, setIsAIChatOpen] = useState(false);
  const [isNewUser, setIsNewUser] = useState(true);

  /* User State & Selections */
  const [userName, setUserName] = useState('사용자님');
  const [userEmail, setUserEmail] = useState('user@farmflate.com');
  const [selectedProvince, setSelectedProvince] = useState('전북특별자치도');
  const [selectedDistrict, setSelectedDistrict] = useState('고창군');
  const [selectedCropName, setSelectedCropName] = useState('감자');
  const [analysisResult, setAnalysisResult] = useState<DynamicAnalysisResult>(
    analyzeCropSuitability('전북특별자치도', '고창군', '감자')
  );
  const [apiReport, setApiReport] = useState<RegionReport | null>(null);
  const [homeData, setHomeData] = useState<HomeData | null>(null);

  // Strict Auth Guard Function
  const safeSetViewStep = (targetStep: ExtendedViewStep) => {
    const isAuth = checkHasToken();
    if (!isAuth && targetStep !== 'landing' && targetStep !== 'splash') {
      console.warn('Unauthenticated user attempt blocked for step:', targetStep);
      setViewStep('landing');
      return;
    }
    setViewStep(targetStep);
  };

  useEffect(() => {
    // Read query params if redirected from OAuth
    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const targetView = params.get('view');
    
    if (token) {
      localStorage.setItem('jwtToken', token);
      // Clean query params after token saved
      window.history.replaceState({}, document.title, window.location.pathname);
    }

    const isAuth = checkHasToken();
    if (!isAuth) {
      safeSetViewStep('landing');
      return;
    }

    if (targetView && (targetView === 'dashboard' || targetView === 'landing' || targetView === 'explore')) {
      safeSetViewStep(targetView as ExtendedViewStep);
    }

    // Sync with Spring Boot API for Kakao user info and region analysis status
    ApiService.getHome()
      .then(resData => {
        setHomeData(resData);
        if (resData?.user?.displayName) {
          setUserName(resData.user.displayName);
        }
        if (resData?.user?.email) {
          setUserEmail(resData.user.email);
        }
        if (resData?.latestRegionAnalysis) {
          setIsNewUser(false);
          ApiService.getRegionReport(resData.latestRegionAnalysis.analysisId)
            .then(rep => {
              setApiReport(rep);
              if (rep.region?.sidoName && rep.region?.sigunguName) {
                setSelectedProvince(rep.region.sidoName);
                setSelectedDistrict(rep.region.sigunguName);
              }
            })
            .catch(() => {});
        } else {
          setIsNewUser(true);
        }
      })
      .catch(err => {
        console.warn('Backend server offline or unauthenticated, using default new user state:', err);
        setIsNewUser(true);
      });
  }, []);

  /* Dynamic Fields & Posts */
  const [myFields, setMyFields] = useState<MyFieldItem[]>(MOCK_MY_FIELDS);
  const [posts, setPosts] = useState<CommunityPost[]>(MOCK_COMMUNITY_POSTS);

  /* Region Analysis Handler */
  const handleStartAnalysis = async (province: string, district: string) => {
    setSelectedProvince(province);
    setSelectedDistrict(district);
    safeSetViewStep('analyzing');

    try {
      // Call real Spring Boot backend for Region Analysis
      const statusRes = await ApiService.createRegionAnalysis({
        sidoCode: getSidoCode(province),
        sigunguCode: getSigunguCode(province, district),
        idempotencyKey: 'key_' + Date.now()
      });

      const rep = await ApiService.getRegionReport(statusRes.analysisId);
      setApiReport(rep);
      if (rep.region?.sidoName && rep.region?.sigunguName) {
        setSelectedProvince(rep.region.sidoName);
        setSelectedDistrict(rep.region.sigunguName);
      }
      setIsNewUser(false);
    } catch (err) {
      console.warn('Backend analysis call fallback to local engine:', err);
    } finally {
      const result = analyzeCropSuitability(province, district, selectedCropName);
      setAnalysisResult(result);
    }
  };

  /* Analysis Completion Handler */
  const handleAnalysisComplete = () => {
    safeSetViewStep('report_summary');
  };

  /* Crop Input Handler */
  const handleStartCropConditionAnalysis = (cropName: string, _stage: string, _mode: string) => {
    setSelectedCropName(cropName);
    const result = analyzeCropSuitability(selectedProvince, selectedDistrict, cropName);
    setAnalysisResult(result);
    safeSetViewStep('analyzing');
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
    
    setIsNewUser(false);
    setMyFields(prev => isNewUser ? [newField] : [newField, ...prev]);
    safeSetViewStep('myfield');
    setActiveTab('myfield');
  };

  /* Tab Navigation Handler */
  const handleTabChange = (tab: TabState) => {
    setActiveTab(tab);
    if (tab === 'home') safeSetViewStep('dashboard');
    if (tab === 'myfield') safeSetViewStep('myfield');
    if (tab === 'community') safeSetViewStep('community');
    if (tab === 'settings') safeSetViewStep('mypage');
  };

  /* Community Handlers */
  const handleToggleLike = (postId: string) => {
    setPosts(prev => prev.map(p => {
      if (p.id === postId) {
        const isLiked = !p.isLiked;
        return {
          ...p,
          isLiked,
          likeCount: isLiked ? p.likeCount + 1 : Math.max(0, p.likeCount - 1)
        };
      }
      return p;
    }));
  };

  const handleToggleSave = (postId: string) => {
    setPosts(prev => prev.map(p => p.id === postId ? { ...p, isSaved: !p.isSaved } : p));
  };

  const handleAddComment = (postId: string, commentText: string) => {
    setPosts(prev => prev.map(p => {
      if (p.id === postId) {
        const newComment = {
          id: 'comm_' + Date.now(),
          author: userName,
          content: commentText,
          timeAgo: '방금 전'
        };
        const updatedComments = p.comments ? [...p.comments, newComment] : [newComment];
        return {
          ...p,
          commentCount: updatedComments.length,
          comments: updatedComments
        };
      }
      return p;
    }));
  };

  const handleCreatePost = (title: string, content: string, category?: string, locationTag?: string, imageUrl?: string) => {
    const newPost: CommunityPost = {
      id: 'post_' + Date.now(),
      category: category || '농가 노하우',
      tagLocation: locationTag || `${selectedProvince} ${selectedDistrict}`,
      title,
      content,
      author: userName,
      timeAgo: '방금 전',
      commentCount: 0,
      likeCount: 0,
      isLiked: false,
      isSaved: false,
      imageUrl,
      comments: []
    };
    setPosts(prev => [newPost, ...prev]);
    safeSetViewStep('community');
    setActiveTab('community');
  };

  return (
    <div className="mobile-wrapper min-h-screen bg-white">
      {/* 0. Splash Screen */}
      {viewStep === 'splash' && (
        <SplashView onComplete={() => safeSetViewStep('landing')} />
      )}

      {/* 1. Landing Screen (Kakao OAuth Login) */}
      {viewStep === 'landing' && (
        <LandingView onLogin={() => safeSetViewStep('dashboard')} />
      )}

      {/* 2. Region Search Screen */}
      {viewStep === 'explore' && (
        <RegionExploreView
          onBack={() => safeSetViewStep('dashboard')}
          onStartAnalysis={handleStartAnalysis}
        />
      )}

      {/* 3. Crop Condition Input Screen */}
      {viewStep === 'condition' && (
        <CropConditionInputView
          onBack={() => safeSetViewStep('dashboard')}
          onStartAnalysis={handleStartCropConditionAnalysis}
          onOpenExplore={() => safeSetViewStep('explore')}
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
          regionName={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : `${selectedProvince} ${selectedDistrict}`}
          report={apiReport}
          onBack={() => safeSetViewStep('explore')}
          onNext={() => safeSetViewStep('report_risks')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 6. Region Risks Screen */}
      {viewStep === 'report_risks' && (
        <RegionRisksView
          report={apiReport}
          onBack={() => safeSetViewStep('report_summary')}
          onNext={() => safeSetViewStep('report_tips')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 7. Region Tips Screen */}
      {viewStep === 'report_tips' && (
        <RegionTipsView
          districtName={apiReport?.region?.sigunguName || selectedDistrict}
          report={apiReport}
          onBack={() => safeSetViewStep('report_risks')}
          onGoToCreateField={() => handleTabChange('myfield')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
        />
      )}

      {/* 8. Recommended Crops Screen */}
      {viewStep === 'recommended_crops' && (
        <RecommendedCropsView
          districtName={apiReport?.region?.sigunguName || selectedDistrict}
          report={apiReport}
          onBack={() => safeSetViewStep('report_tips')}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          onSelectCrop={(cropName) => {
            setSelectedCropName(cropName);
            const result = analyzeCropSuitability(selectedProvince, selectedDistrict, cropName);
            setAnalysisResult(result);
            safeSetViewStep('crop_suitability_report');
          }}
        />
      )}

      {/* 9. Crop Suitability Report Screen */}
      {viewStep === 'crop_suitability_report' && (
        <CropSuitabilityReportView
          cropName={selectedCropName}
          score={analysisResult.score}
          onBack={() => safeSetViewStep('recommended_crops')}
          onRegisterCrop={handleAddField}
        />
      )}

      {/* 10. Main Dashboard Screen */}
      {viewStep === 'dashboard' && (
        <MainDashboardView
          userName={userName}
          analyzedRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : `${selectedProvince} ${selectedDistrict}`}
          analyzedCropResult={null}
          homeData={homeData}
          onGoToExplore={() => safeSetViewStep('explore')}
          onOpenReport={() => safeSetViewStep('report_summary')}
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
          onAddField={() => safeSetViewStep('explore')}
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
          onOpenWrite={() => safeSetViewStep('community_create')}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          onToggleLike={handleToggleLike}
          onToggleSave={handleToggleSave}
          onAddComment={handleAddComment}
        />
      )}

      {/* 13. Community Create Screen */}
      {viewStep === 'community_create' && (
        <CommunityCreatePostView
          userRegion={`${selectedProvince} ${selectedDistrict}`}
          onCancel={() => safeSetViewStep('community')}
          onSubmitPost={handleCreatePost}
        />
      )}

      {/* 14. My Page (Settings) Screen */}
      {viewStep === 'mypage' && (
        <MyPageView
          userName={userName}
          userEmail={userEmail}
          userRegion={apiReport?.region?.sidoName && apiReport?.region?.sigunguName ? `${apiReport.region.sidoName} ${apiReport.region.sigunguName}` : `${selectedProvince} ${selectedDistrict}`}
          posts={posts}
          onOpenAIChat={() => setIsAIChatOpen(true)}
          activeTab={activeTab}
          onTabChange={handleTabChange}
          onGoToExplore={() => safeSetViewStep('explore')}
          onLogout={() => {
            localStorage.clear();
            safeSetViewStep('landing');
          }}
          onToggleLike={handleToggleLike}
          onToggleSave={handleToggleSave}
          onAddComment={handleAddComment}
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
