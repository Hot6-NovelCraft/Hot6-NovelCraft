package com.example.hot6novelcraft.domain.user.entity.enums;

public enum ReadingGoal {

    LIGHT("가볍게 즐기기", "주 1~2편")
    , STEADY("꾸준히 시작하기", "주 3~4편")
    , PASSIONATE("열독가", "매일 읽기")
    , CUSTOM("직접 설정", "설정 안함");

    private final String label;
    private final String description;

    ReadingGoal(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
