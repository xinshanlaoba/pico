package com.picojava.shell;

import com.picojava.agent.Pico;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class ShellSessionState {
    private Pico pico;

    public boolean hasActivePico() {
        return pico != null;
    }

    public void clear() {
        pico = null;
    }
}
