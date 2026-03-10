package top.asimov.pigeon.model.response;

import top.asimov.pigeon.model.entity.User;

public record AuthStatusResponse(boolean authEnabled, boolean loginCaptchaEnabled, User user) {
}
