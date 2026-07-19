package io.github.gdpl2112.dg_bot.pack;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author github.kloping
 */
@Data
public class PassiveMessage {
    private Integer id;
    private String qid;
    private String touch;
    private List<String> outs = new ArrayList<>();
}
