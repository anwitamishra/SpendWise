package com.spendwise.service;

import com.spendwise.dto.CreateGroupRequest;
import com.spendwise.exception.BadRequestException;
import com.spendwise.exception.ResourceNotFoundException;
import com.spendwise.model.ExpenseGroup;
import com.spendwise.model.GroupMember;
import com.spendwise.model.User;
import com.spendwise.repository.ExpenseGroupRepository;
import com.spendwise.repository.GroupMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

@Service
public class ExpenseGroupService {

    private final ExpenseGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserService userService;

    private static final String ALPHANUM = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom random = new SecureRandom();

    public ExpenseGroupService(ExpenseGroupRepository groupRepository,
                               GroupMemberRepository memberRepository,
                               UserService userService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userService = userService;
    }

    private String generateUniqueInviteCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder("SW-");
            for (int j = 0; j < 6; j++) {
                sb.append(ALPHANUM.charAt(random.nextInt(ALPHANUM.length())));
            }
            String code = sb.toString();
            if (!groupRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        return "SW-" + System.currentTimeMillis();
    }

    @Transactional
    public ExpenseGroup createGroup(String guestIdentifier, CreateGroupRequest req) {
        if (req.getGroupName() == null || req.getGroupName().trim().isEmpty()) {
            throw new BadRequestException("Group name is required");
        }

        User user = userService.getUserByGuestIdentifier(guestIdentifier);

        ExpenseGroup group = new ExpenseGroup();
        group.setGroupName(req.getGroupName().trim());
        group.setDescription(req.getDescription() != null ? req.getDescription().trim() : "");
        group.setCreatedBy(user.getId());
        group.setInviteCode(generateUniqueInviteCode());

        ExpenseGroup savedGroup = groupRepository.save(group);

        // Add creator as first member
        GroupMember creatorMember = new GroupMember();
        creatorMember.setGroupId(savedGroup.getId());
        creatorMember.setUserId(user.getId());
        creatorMember.setDisplayName(user.getDisplayName());
        memberRepository.save(creatorMember);

        savedGroup.setCreatorName(user.getDisplayName());
        savedGroup.setMemberCount(1);
        return savedGroup;
    }

    public List<ExpenseGroup> getGroupsForUser(String guestIdentifier) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        return groupRepository.findByUserId(user.getId());
    }

    public ExpenseGroup getGroupById(String guestIdentifier, Long groupId) {
        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        ExpenseGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        if (!memberRepository.isMember(groupId, user.getId())) {
            throw new BadRequestException("You are not a member of this group");
        }

        group.setMemberCount(memberRepository.countByGroupId(groupId));
        return group;
    }

    public ExpenseGroup previewGroupByInviteCode(String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new BadRequestException("Invite code is required");
        }
        ExpenseGroup group = groupRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("No group found with invite code: " + inviteCode));

        group.setMemberCount(memberRepository.countByGroupId(group.getId()));
        return group;
    }

    @Transactional
    public ExpenseGroup joinGroup(String guestIdentifier, String inviteCode) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new BadRequestException("Invite code is required");
        }

        User user = userService.getUserByGuestIdentifier(guestIdentifier);
        ExpenseGroup group = groupRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code: " + inviteCode));

        Optional<GroupMember> existingMember = memberRepository.findByGroupIdAndUserId(group.getId(), user.getId());
        if (existingMember.isPresent()) {
            // Already joined
            group.setMemberCount(memberRepository.countByGroupId(group.getId()));
            return group;
        }

        GroupMember newMember = new GroupMember();
        newMember.setGroupId(group.getId());
        newMember.setUserId(user.getId());
        newMember.setDisplayName(user.getDisplayName());
        memberRepository.save(newMember);

        group.setMemberCount(memberRepository.countByGroupId(group.getId()));
        return group;
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return memberRepository.findByGroupId(groupId);
    }
}
