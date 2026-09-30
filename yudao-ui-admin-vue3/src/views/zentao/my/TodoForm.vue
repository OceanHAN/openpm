<template>
  <el-dialog v-model="visible" :title="form.id ? '修改待办' : '新建待办'" width="620px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="待办是个人清单，不是任务"
      description="zt_todo 记的是「我今天要干的几件事」，可以不挂任何项目；也可以选一个类型（任务/Bug/需求/测试单）当快捷入口。关闭待办会把指派人置成伪用户 closed，激活时再还原。"
    />
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="待办名称" prop="name">
        <el-input v-model="form.name" placeholder="要做什么" maxlength="150" show-word-limit />
      </el-form-item>
      <el-form-item label="日期" prop="date">
        <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" class="!w-180px" />
        <span class="ml-10px text-gray-500 text-12px">不填默认今天</span>
      </el-form-item>
      <el-form-item label="时间">
        <el-input v-model="form.begin" placeholder="0900" maxlength="4" class="!w-100px" />
        <span class="mx-8px">~</span>
        <el-input v-model="form.end" placeholder="1000" maxlength="4" class="!w-100px" />
      </el-form-item>
      <el-form-item label="类型" prop="type">
        <el-select v-model="form.type" class="!w-160px" @change="handleTypeChange">
          <el-option v-for="o in TODO_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <template v-if="needObject">
          <span class="ml-10px text-gray-500 text-12px">关联对象编号</span>
          <el-input-number v-model="form.objectID" :min="1" :controls="false" class="!w-140px ml-8px" />
        </template>
      </el-form-item>
      <el-form-item label="优先级" prop="pri">
        <el-select v-model="form.pri" class="!w-160px">
          <el-option v-for="o in TODO_PRI_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="指派给" prop="assignedTo">
        <el-select v-model="form.assignedTo" filterable allow-create default-first-option clearable
                   placeholder="默认指派给自己" class="!w-240px">
          <el-option v-for="u in userList" :key="u.account" :label="`${u.realname || u.account}（${u.account}）`"
                     :value="u.account!" />
        </el-select>
      </el-form-item>
      <el-form-item label="私有">
        <el-switch v-model="form.privateFlag" :active-value="1" :inactive-value="0" />
        <span class="ml-10px text-gray-500 text-12px">私有待办在别人那里只显示「这是私有待办」</span>
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.desc" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as TodoApi from '@/api/zentao/todo'
import * as OrgApi from '@/api/zentao/organization'
import { TODO_TYPE_OPTIONS, TODO_PRI_OPTIONS } from '@/api/zentao/todo'

defineOptions({ name: 'ZentaoTodoForm' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const saving = ref(false)
const formRef = ref()
const userList = ref<OrgApi.OrgUserVO[]>([])
const needObject = computed(() => !!form.type && form.type !== 'custom' && form.type !== 'cycle')

const today = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const form = reactive<TodoApi.TodoVO>({
  id: undefined,
  name: '',
  date: today(),
  begin: '',
  end: '',
  type: 'custom',
  objectID: undefined,
  pri: 3,
  assignedTo: '',
  privateFlag: 0,
  desc: ''
})

const rules = {
  name: [{ required: true, message: '请输入待办名称', trigger: 'blur' }],
  date: [{ required: true, message: '请选择日期', trigger: 'change' }]
}

const loadUsers = async () => {
  if (userList.value.length) return
  try {
    userList.value = await OrgApi.getUserList({})
  } catch {
    userList.value = []
  }
}

const handleTypeChange = () => {
  if (!needObject.value) form.objectID = undefined
}

const open = async (mode: 'create' | 'edit', row?: TodoApi.TodoVO) => {
  await loadUsers()
  if (mode === 'edit' && row) {
    Object.assign(form, {
      id: row.id,
      name: row.name,
      date: row.date,
      begin: row.begin,
      end: row.end,
      type: row.type || 'custom',
      objectID: row.objectID || undefined,
      pri: row.pri ?? 3,
      assignedTo: row.assignedTo,
      privateFlag: row.privateFlag ?? 0,
      desc: row.desc
    })
  } else {
    Object.assign(form, {
      id: undefined,
      name: '',
      date: today(),
      begin: '',
      end: '',
      type: 'custom',
      objectID: undefined,
      pri: 3,
      assignedTo: '',
      privateFlag: 0,
      desc: ''
    })
  }
  visible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  if (needObject.value && !form.objectID) {
    message.warning('这种类型的待办必须填关联对象编号')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await TodoApi.updateTodo(form)
      message.success('待办已修改')
    } else {
      await TodoApi.createTodo(form)
      message.success('待办已创建')
    }
    visible.value = false
    emit('success')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>
