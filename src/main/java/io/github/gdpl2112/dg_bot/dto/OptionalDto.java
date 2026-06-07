package io.github.gdpl2112.dg_bot.dto;

import io.github.gdpl2112.dg_bot.dao.Optional;
import lombok.Getter;

import java.util.List;

/**
 * @author github.kloping
 */
@Getter
public class OptionalDto extends Optional {
    private String desc;
    private String name;
    /** 该功能已启用的群/好友 tid 列表 */
    private List<String> groups;

    public static OptionalDto of(Optional o, String name, String desc, List<String> groups) {
        OptionalDto dto = new OptionalDto();
        dto.desc = desc;
        dto.name = name;
        dto.groups = groups;
        dto.setQid(o.getQid());
        dto.setOpt(o.getOpt());
        dto.setTid(o.getTid());
        dto.setOpen(o.getOpen());
        return dto;
    }
}
