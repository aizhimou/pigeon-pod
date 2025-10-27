package top.asimov.pigeon.config;

import cn.dev33.satoken.apikey.loader.SaApiKeyDataLoader;
import cn.dev33.satoken.apikey.model.ApiKeyModel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import top.asimov.pigeon.mapper.UserMapper;
import top.asimov.pigeon.model.entity.User;

@Component
public class SaApiKeyDataLoaderImpl implements SaApiKeyDataLoader {

  private final UserMapper userMapper;

  public SaApiKeyDataLoaderImpl(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  @Override
  public Boolean getIsRecordIndex() {
    return false;
  }

  @Override
  public ApiKeyModel getApiKeyModelFromDatabase(String namespace, String apiKey) {
    User user = userMapper.selectOne(new QueryWrapper<User>().eq("api_key", apiKey));
    if (ObjectUtils.isEmpty(user)) {
      return null; // No user found with this API key
    }
    ApiKeyModel akModel = new ApiKeyModel();
    akModel.setLoginId(user.getId());
    akModel.setApiKey(apiKey);
    akModel.setTitle(user.getUsername());
    akModel.setExpiresTime(-1);
    return akModel;
  }

}
