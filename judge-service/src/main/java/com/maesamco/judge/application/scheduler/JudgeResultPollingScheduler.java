package com.maesamco.judge.application.scheduler;

import com.maesamco.judge.application.facade.JudgeResultPollingFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JudgeResultPollingScheduler {

    private final JudgeResultPollingFacade judgeResultPollingFacade;

    @Scheduled(fixedDelay = 2000)
    public void poll() {
        judgeResultPollingFacade.pollAndReflect();
    }
}
