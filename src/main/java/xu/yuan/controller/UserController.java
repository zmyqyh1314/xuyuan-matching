package xu.yuan.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import xu.yuan.enums.ErrorCode;
import xu.yuan.Common.Result;
import xu.yuan.Common.ResultUtils;
import xu.yuan.Eception.BusinessEception;
import xu.yuan.model.domain.User;
import xu.yuan.model.request.UserLoginRequest;
import xu.yuan.model.request.UserRegisterRequest;
import xu.yuan.service.UserService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static xu.yuan.Constant.UserConstant.*;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/user")
@Slf4j
//允许这个IP跨域
public class UserController {
    @Autowired
    private UserService userService;
    @Resource
    private RedisTemplate redisTemplate;
    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public Result<Long> UserRgister(@RequestBody UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String planetCode = userRegisterRequest.getPlanetCode();
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }

        long result = userService.registerUser(userAccount, userPassword, checkPassword, planetCode);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * @return
     */
    @PostMapping("/login")
    public Result<User> Userlogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest httpServletRequest) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        User user = userService.doLogin(userAccount, userPassword, httpServletRequest);
        System.out.println("你好鱼友，我是许苑向上");
        return ResultUtils.success(user);
    }

    /**
     * 用户注销
     *
     * @return
     */
    @PostMapping("/logout")
    public Result<Integer> Userlogout(HttpServletRequest httpServletRequest) {
        if (httpServletRequest == null) {
            return null;
        }

        Integer i = userService.userLogout(httpServletRequest);
        return ResultUtils.success(i);
    }

    /**
     * 获取当前用户
     *
     * @param request
     * @return
     */
    @GetMapping("/current")
    public Result<User> getCurrentUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null) {
            throw new BusinessEception(ErrorCode.NOT_LOGIN);
        }
        long userId = currentUser.getId();
        // TODO 校验用户是否合法
        User user = userService.getById(userId);
        User safetyUser = userService.getSaftyUser(user);
        return ResultUtils.success(safetyUser);

    }

    /**
     * 用户根据username查询
     *
     * @param username
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/search")
    public Result<List<User>> searchUser(String username, HttpServletRequest httpServletRequest) {
        //仅管理员可查
        if (!isAdmin(httpServletRequest)) {
            throw new BusinessEception(ErrorCode.NO_AUTH, "缺少管理员权限");
        }

        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            lqw.like(User::getUsername, username);
        }
        List<User> userList = userService.list(lqw);

        List<User> NewUserList = userList.stream().map(user -> userService.getSaftyUser(user)).collect(Collectors.toList());
        return ResultUtils.success(NewUserList);
    }


    /**
     * 默认显示全部用户
     * @param pageSize
     * @param pageNum
     * @param httpServletRequest
     * @return
     */
    @GetMapping("/recommend")
    public Result<Page<User>> recommendUser(long pageSize, long pageNum , HttpServletRequest httpServletRequest) {
//        User logUser = userService.getLogUser(httpServletRequest);
        ValueOperations<String,Object> redis = redisTemplate.opsForValue();
        // key的命名
//        String key = String.format("yupao:user:recommend:%s", logUser.getId());
        String key = String.format("yupao:user:recommend");
        // 查看是否有缓存
        Page<User> pageList = (Page<User>) redis.get(key);
        //有直接返回
        if (pageList != null){
            return ResultUtils.success(pageList);
        }
        //没有,查询数据库
        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        Page<User> page = new Page<>(pageSize,pageNum);
        pageList = userService.page(page, lqw);
        // 防止设置key时候错误还是返回数据``````
        try {
            //并写入redis中
            redis.set(key,pageList,1, TimeUnit.MINUTES);
        } catch (Exception e) {
        log.info("redis set key: error {}",e);
        }

        return ResultUtils.success(pageList);
    }

    @PostMapping("/delete")
    public boolean DeleteUser(@RequestBody long id, HttpServletRequest httpServletRequest) {
        //仅管理员可查
        if (!isAdmin(httpServletRequest)) {
            return false;
        }
        if (id <= 0) {
            return false;
        }
        return userService.removeById(id);
    }

    /**
     * 根据用户标签搜索
     *
     * @param tagNameList
     * @return
     */
    /*required = false 表示该参数是可选的，即它不是必需的。如果客户端请求没有提供
    tagNameList 参数，Spring 将会将其值设为 null 或空列表（取决于具体的实现和配置）。*/
    @GetMapping("/search/tags")
    public Result<List<User>> SearchUserByTags(@RequestParam(required = false) List<String> tagNameList) {
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessEception(ErrorCode.PARAMS_ERROR);
        }
        List<User> usersList = userService.searchUserByTag(tagNameList);
        return ResultUtils.success(usersList);

    }

    /**
     * 是否为管理员
     *
     * @param httpServletRequest
     * @return
     */
    public boolean isAdmin(HttpServletRequest httpServletRequest) {

        //仅管理员可查
        User user = (User) httpServletRequest.getSession().getAttribute(USER_LOGIN_STATE);

        return (user != null && user.getRole() == ADMIN_ROLE);
    }

    /**
     * 修改用户本人信息
     */
    @PostMapping("update")
    public Result<Integer> updateUser(@RequestBody User user,HttpServletRequest request) {
        // 1.校验参数是否为空
        if (user == null) {
            throw new BusinessEception(ErrorCode.NULL_ERROR);
        }
        // 2.校验是否有权限 ,logUser表示当前登录用户
        User logUser = userService.getLogUser(request);
        // 3.进行用户信息修改
        int result = userService.updateUser(user,logUser);
        return ResultUtils.success(result);

        // 3.触发跟新


    }

}
