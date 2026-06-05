package top.asimov.pigeon.config;

import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import org.springframework.stereotype.Component;
import top.asimov.pigeon.model.entity.User;
import top.asimov.pigeon.mapper.UserMapper;

@Component
public class StpInterfaceImpl implements StpInterface {

  private final UserMapper userMapper;

  public StpInterfaceImpl(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    return List.of();
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    User user = userMapper.selectById(loginId.toString());
    if (user != null && user.getRole() != null) {
      return List.of(user.getRole());
    }
    return List.of("user");
  }

  @Override
  public SaDisableWrapperInfo isDisabled(Object loginId, String service) {
    return StpInterface.super.isDisabled(loginId, service);
  }
}
