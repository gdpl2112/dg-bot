package io.github.gdpl2112.dg_bot.dto;

import io.github.gdpl2112.dg_bot.dao.Optional;
import lombok.Getter;

/**
 * @author github.kloping
 */
@Getter
public class OptionalDto extends Optional {
    private String desc;
    private String name;

    public static OptionalDto of(Optional o, String name, String desc) {
        OptionalDto dto = new OptionalDto();
        dto.desc = desc;
        dto.name = name;
        dto.setQid(o.getQid());
        dto.setOpt(o.getOpt());
        dto.setOpen(o.getOpen());
        return dto;
    }
}
