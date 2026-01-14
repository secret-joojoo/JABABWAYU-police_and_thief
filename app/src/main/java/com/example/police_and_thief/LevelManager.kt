package com.example.police_and_thief

object LevelManager {

    // [로직 1] 레벨별 최대 경험치 구하는 공식
    // (나중에 난이도 조절할 때 이 숫자만 바꾸면 앱 전체에 적용됨!)
    fun getMaxExp(level: Int): Int {
        return level * 100
    }

    // [로직 2] 경험치 획득 후 새로운 레벨과 경험치 계산
    fun calculateNewStats(currentLevel: Int, currentExp: Int, earnedExp: Int): Pair<Int, Int> {
        var newLevel = currentLevel
        var newExp = currentExp + earnedExp

        // 현재 레벨의 최대 경험치 가져오기
        var maxExp = getMaxExp(newLevel)

        // 경험치 통이 넘치면 레벨업 (여러 단계 레벨업 가능)
        while (newExp >= maxExp) {
            newExp -= maxExp    // 경험치 차감
            newLevel++          // 레벨 상승
            maxExp = getMaxExp(newLevel) // 다음 레벨통 크기 갱신
        }

        // 결과 반환 (새 레벨, 새 경험치)
        return Pair(newLevel, newExp)
    }
}