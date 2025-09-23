package io.github.gdpl2112.dg_bot.service.listenerhosts.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.gdpl2112.dg_bot.dao.Administrator;
import io.github.gdpl2112.dg_bot.dao.GroupConf;
import io.github.gdpl2112.dg_bot.mapper.AdministratorMapper;
import io.github.gdpl2112.dg_bot.mapper.GroupConfMapper;
import net.mamoe.mirai.contact.Contact;
import net.mamoe.mirai.contact.User;

import java.util.List;

/**
 *
 * create on 21:55
 *
 * @author github kloping
 * @since 2025/9/21
 */
public class AdminMenuState implements BotState {

    private AdministratorMapper administratorMapper;
    private GroupConfMapper groupConfMapper;

    public AdminMenuState(AdministratorMapper administratorMapper, GroupConfMapper groupConfMapper) {
        this.administratorMapper = administratorMapper;
        this.groupConfMapper = groupConfMapper;
    }

    @Override
    public String getName() {
        return "管理设置";
    }

    @Override
    public String getWelcomeMessage() {
        return "请输入选项"
                + "\n1.查看管理"
                + "\n2.设置开关";
    }

    @Override
    public String handleInput(User user, String input, Context context) {
        switch (input) {
            case "1":
                return context.pushState(new AdminManagerState(context.getBot().getId()));
            case "2":
                return context.pushState(new GroupConfState(context.getBot().getId()));
            default:
                return "无效输入";
        }
    }

    public class GroupConfState implements BotState {
        @Override
        public String getName() {
            return "开关设置";
        }

        private Long bid;

        public GroupConfState(long bid) {
            this.bid = bid;
        }

        @Override
        public String getWelcomeMessage() {
            return "输入指定 群聊/好友 ID以查看或操作开关!\n 0. 退出!";
        }

        private GroupConf groupConf;

        @Override
        public String handleInput(User user, String input, Context context) {
            if (groupConf != null) {
                try {
                    Integer v = Integer.valueOf(input);
                    if (v >= 1 && v <= 4) {
                        if (v == 1) {
                            groupConf.setK0(!groupConf.getK0());
                        } else if (v == 2) {
                            groupConf.setK1(!groupConf.getK1());
                        } else if (v == 3) {
                            groupConf.setK2(!groupConf.getK2());
                        } else if (v == 4) {
                            groupConf.setK3(!groupConf.getK3());
                        }
                        groupConfMapper.update(groupConf, new LambdaQueryWrapper<GroupConf>()
                                .eq(GroupConf::getQid, bid)
                                .eq(GroupConf::getTid, groupConf.getTid()));
                        return getConfState(groupConf.getTid(), groupConf).toString();
                    }
                } catch (NumberFormatException e) {
                }
            }

            try {
                Long tid = Long.parseLong(input);
                Contact contact = null;
                String p = "g";
                contact = context.getBot().getGroup(tid);
                if (contact == null) {
                    contact = context.getBot().getFriend(tid);
                    p = "f";
                }
                if (contact == null) return "不存在该ID的群聊或好友!";
                String nid = p + tid;
                groupConf = groupConfMapper.selectOne(new LambdaQueryWrapper<GroupConf>()
                        .eq(GroupConf::getQid, bid).eq(GroupConf::getTid, nid));
                if (groupConf == null) {
                    groupConf = new GroupConf();
                    groupConf.setQid(String.valueOf(bid));
                    groupConf.setTid(nid);
                    groupConf.setK0(true);
                    groupConf.setK1(true);
                    groupConf.setK2(true);
                    groupConf.setK3(true);
                    groupConfMapper.insert(groupConf);
                }
                StringBuilder sb = getConfState(nid, groupConf);
                return sb.toString();
            } catch (NumberFormatException e) {
            }
            return "无效输入!";
        }

        private StringBuilder getConfState(String nid, GroupConf groupConf) {
            StringBuilder sb = new StringBuilder("群聊/好友 ID: ").append(nid).append("\n输入指定序号切换开关!\n输入其他ID查看其它开关状态");
            if (groupConf == null) {
                sb.append("\n 1. ").append("调用开关:").append("开");
                sb.append("\n 2. ").append("监听开关:").append("开");
                sb.append("\n 3. ").append("回复开关:").append("开");
                sb.append("\n 4. ").append("功能开关:").append("开");

            } else {
                sb.append("\n 1. ").append("调用开关:").append(groupConf.getK0() ? "开" : "关");
                sb.append("\n 2. ").append("监听开关:").append(groupConf.getK1() ? "开" : "关");
                sb.append("\n 3. ").append("回复开关:").append(groupConf.getK2() ? "开" : "关");
                sb.append("\n 4. ").append("功能开关:").append(groupConf.getK3() ? "开" : "关");
            }
            return sb;
        }
    }

    public class AdminManagerState implements BotState {
        @Override
        public String getName() {
            return "设置管理";
        }

        private Long bid;

        public AdminManagerState(long id) {
            this.bid = id;
            update();
        }

        private void update() {
            administrators = administratorMapper.selectList(new LambdaQueryWrapper<Administrator>().eq(Administrator::getQid, bid));
        }

        private List<Administrator> administrators;

        @Override
        public String getWelcomeMessage() {
            StringBuilder sb = new StringBuilder("已存在管理QQ:");
            sb.append("\n输入序号 删除对应管理\n输入QQ添加管理\n 0. 退出!");
            int i = 0;
            for (Administrator administrator : administrators) {
                sb.append("\n ").append(++i).append(". ").append(administrator.getTargetId());
            }
            return sb.toString();
        }

        @Override
        public String handleInput(User user, String input, Context context) {
            try {
                Integer v = Integer.parseInt(input);
                if (v > 0 && v <= administrators.size()) {
                    Administrator administrator = administrators.get(v - 1);
                    administratorMapper.delete(new LambdaQueryWrapper<Administrator>()
                            .eq(Administrator::getQid, administrator.getQid())
                            .eq(Administrator::getTargetId, administrator.getTargetId())
                    );
                    update();
                    return "删除成功!\n\n" + getWelcomeMessage();
                }
            } catch (NumberFormatException e) {
            }
            try {
                Long tid = Long.parseLong(input);
                Administrator administrator = administratorMapper.selectOne(new LambdaQueryWrapper<Administrator>()
                        .eq(Administrator::getQid, bid).eq(Administrator::getTargetId, tid));
                if (administrator == null) {
                    administrator = new Administrator();
                    administrator.setQid(bid.toString());
                    administrator.setTargetId(tid.toString());
                    administratorMapper.insert(administrator);
                    update();
                    return "添加成功!\n\n" + getWelcomeMessage();
                }
                return input + "已是管理!";
            } catch (NumberFormatException e) {
            }
            return "无效输入";
        }
    }
}
