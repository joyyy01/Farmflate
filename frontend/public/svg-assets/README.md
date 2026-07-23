# Farmflate SVG Asset Library v2

- 총 SVG: 163개 + UI sprite 1개
- 모든 주요 SVG는 외부 이미지, 외부 폰트, foreignObject 없이 독립 실행됩니다.
- 한글 및 영문 텍스트는 글꼴 파일을 포함하지 않고 벡터 패스로 변환했습니다.
- 복수 SVG를 한 화면에 인라인해도 충돌하지 않도록 gradient ID를 파일별로 고유하게 생성했습니다.
- 투명 배경 에셋은 checkerboard 미리보기에서 확인할 수 있습니다.

## 폴더

- `brand/`: 로고, 마스코트, 표정
- `illustrations/landing/`: 랜딩 히어로와 분리 요소
- `weather/`, `crops/`, `ai/`, `report/`: 도메인 일러스트
- `ui-icons/`: 24×24 라인/채움 아이콘 및 sprite
- `components/`: 버튼, 칩, 배지, 입력, 카드, 차트, 네비게이션, 리스트 행
- `screens/`: 전체 화면 참고 SVG
- `tokens/`: 색상·간격·라운드
- `preview/index.html`: 전체 에셋 브라우저

## 사용

```html
<img src="assets/crops/potato.svg" alt="감자">
```

UI sprite는 `<use href="ui-icons/sprite.svg#ff-home">` 방식으로 사용할 수 있습니다.
