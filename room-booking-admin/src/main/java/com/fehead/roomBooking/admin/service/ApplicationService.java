package com.fehead.roomBooking.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fehead.roomBooking.admin.mapper.ApplicationMapper;
import com.fehead.roomBooking.admin.mapper.RoomMapper;
import com.fehead.roomBooking.admin.mapper.RoomStatusMapper;
import com.fehead.roomBooking.common.entity.Application;
import com.fehead.roomBooking.common.entity.RoomStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ApplicationService {
    private ApplicationMapper applicationMapper;
    private RoomStatusMapper roomStatusMapper;
    private RoomMapper roomMapper;

    public ApplicationService(ApplicationMapper applicationMapper, RoomStatusMapper roomStatusMapper, RoomMapper roomMapper) {
        this.applicationMapper = applicationMapper;
        this.roomStatusMapper = roomStatusMapper;
        this.roomMapper = roomMapper;
    }

    /**
    增加application 同时添加对应房间状态  检查是否与其他申请的时间重合
     */
    public Boolean addApplication(Application application){
        if (!(this.isDuplicate(application))) {
            this.roomStatus(application, 0);
            int insert = applicationMapper.insert(application);
            if (insert != 0) {
                log.info("申请增加成功");
                return true;
            } else {
                log.warn("申请插入失败");
               throw new  RuntimeException("申请插入失败");
            }
        }else {
            log.warn("申请的时间重复");
            throw new RuntimeException("申请的时间重复");
        }
    }
    //返回所有application的list  分页
    public List<Application> getAllApplication(int pageNum){
        //当前页 每页大小
        Page<Application> applicationPage=new Page<>(pageNum,5);
        applicationMapper.selectPage(applicationPage,null);
        List<Application> records = applicationPage.getRecords();
        records.forEach(record->record.setRoom(roomMapper.selectById(record.getRoomId())));
        return records;
    }

    //按照id返回
    public Application getApplicationById(int id){
        Application application =applicationMapper.selectById(id);
        application.setRoom(roomMapper.selectById(application.getRoomId()));
        return application;
    }

    //按照条件返回信息相同的application的list
    public List<Application> getApplicationByMap(Map<String,String> map){
        QueryWrapper<Application> queryWrapper = new QueryWrapper<>();
        map.forEach(queryWrapper::like);
        List<Application> applications = applicationMapper.selectList(queryWrapper);
        applications.forEach(application -> application.setRoom(roomMapper.selectById(application.getRoomId())));
        return applications;
    }
    //删除id对应的申请和房间状态
    public Boolean deleteById( Integer id){
        Application application = applicationMapper.selectById(id);
        Integer roomStatusId = application.getRoomStatusId();
        int deleteRoomStatus = roomStatusMapper.deleteById(roomStatusId);
        int deleteApplication = applicationMapper.deleteById(id);
        if (deleteApplication!=0&&deleteRoomStatus!=0){
            log.info("管理员删除了请求,id信息为"+id);
            return true;
        }else {
            return false;
        }
    }
    //id 修改application 可能需要修改对应的房间状态
    public Boolean modifyApplication(Integer id, Application application) {
//        this.isParamEnough(application);
        application.setId(id);
        //新增和修改时间时检查时间重复
        if (!this.isDuplicate(application)){
            int update = applicationMapper.updateById(application);
            //修改对应的房间状态
            this.roomStatus(application,1);
            if (update!=0){
                log.info("管理员修改了请求,id为"+id);
                return true;
            }else {
                return false;
            }
        }else {
             throw new RuntimeException("申请时间重复");
        }
    }

    //检查新申请的时间是否重复
    public Boolean isDuplicate(Application application){
        AtomicBoolean b = new AtomicBoolean(false);
        //获取房间状态 进行比较
        QueryWrapper<RoomStatus> queryWrapper=new QueryWrapper<>();
        //ge 大于等于 le小于等于 相同数据可能显示不重复
        queryWrapper.ge("start_stamp",application.getStartStamp());
             queryWrapper.le("end_stamp",application.getEndStamp());
        List<RoomStatus> roomStatuses = roomStatusMapper.selectList(queryWrapper);
        if (roomStatuses.size()==0){
            log.info("申请未重复");
          return false;
        }else {
//            for (RoomStatus roomStatus : roomStatuses){
//                if(roomStatus.getId().equals(application.getId())){
//                    return false;
//                }
//            }
         for (RoomStatus roomStatus : roomStatuses){
           if ( roomStatus.getStatus()==0){
               log.info("房间已经使用");
               return true;
           }
         }
        }
        log.info("申请时间重复");
        return true;

    }

    //检查参数是否足够
    public Boolean isParamEnough( Application application){
        if (application.getApplicationStamp()==null||
                application.getStatus()==null||
                application.getRoomStatusId()==null||
                application.getStartStamp()==null||
                application.getEndStamp()==null||
                application.getUserId()==null||
                application.getRoomId()==null){
           throw  new RuntimeException("数据缺少");
        }
        return true;
    }
    //添加或修改对应房间状态 0 1
    public Boolean roomStatus(Application application, int i){
        RoomStatus roomStatus=new RoomStatus();
        roomStatus.setRoomId(application.getRoomId());
        roomStatus.setStartStamp(application.getStartStamp());
        roomStatus.setEndStamp(application.getEndStamp());
        //通过则房间不可用
        if (application.getStatus().equals(1)){
            roomStatus.setStatus(1);
        }else {
            roomStatus.setStatus(0);
        }
        int insert=0;
        switch (i){
            case 0:
                 insert = roomStatusMapper.insert(roomStatus);
                 application.setRoomStatusId(roomStatus.getId());
                if (insert!=0){
                    log.info("添加房间状态成功");
                }else {
                    throw new  RuntimeException("房间状态插入时出现问题");
                }
                break;
            case 1:
                insert = roomStatusMapper.updateById(roomStatus);
                if (insert!=0){
                    log.info("修改房间状态成功");
                }else {
                    throw new  RuntimeException("房间状态修改时出现问题");
                }
                break;
        }

        return  true;
    }
    //序列化为正常的long

}
